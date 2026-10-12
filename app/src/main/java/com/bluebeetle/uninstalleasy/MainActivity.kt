package com.bluebeetle.uninstalleasy

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private data class AppItem(
        val name: String,
        val packageName: String,
        val installTime: Long,
        val lastUsedTime: Long,
        val usageMs: Long,
        val applicationInfo: ApplicationInfo
    )

    private val allApps = mutableListOf<AppItem>()
    private val visibleApps = mutableListOf<AppItem>()

    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AppAdapter

    private val sortOptions = listOf(
        "Newest installed",
        "Oldest installed",
        "Most used (7 days)",
        "Least used (7 days)",
        "Recently used",
        "Name A-Z"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::listView.isInitialized) {
            loadApps()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "Uninstall Easy"
            textSize = 28f
            setTextColor(Color.rgb(28, 28, 30))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Find apps fast. Sort by install date or usage, then uninstall."
            textSize = 14f
            setTextColor(Color.rgb(90, 90, 95))
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(subtitle)

        val usageButton = Button(this).apply {
            text = "Grant usage access"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        root.addView(usageButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        searchBox = EditText(this).apply {
            hint = "Search apps"
            isSingleLine = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilterAndSort()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        sortSpinner = Spinner(this)
        sortSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilterAndSort()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(sortSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        statusText = TextView(this).apply {
            text = "Loading apps..."
            textSize = 13f
            setTextColor(Color.rgb(90, 90, 95))
        }
        infoRow.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        infoRow.addView(progressBar, LinearLayout.LayoutParams(dp(28), dp(28)))
        root.addView(infoRow)

        listView = ListView(this).apply {
            dividerHeight = 1
            setBackgroundColor(Color.WHITE)
        }
        adapter = AppAdapter()
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Loading apps..."

        Thread {
            val usageByPackage = loadUsageStats()
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != packageName }
                .filter { appInfo ->
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    !isSystem && !isUpdatedSystem
                }
                .map { appInfo ->
                    val packageInfo = try {
                        pm.getPackageInfo(appInfo.packageName, 0)
                    } catch (_: Exception) {
                        null
                    }
                    val usage = usageByPackage[appInfo.packageName]
                    AppItem(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName,
                        installTime = packageInfo?.firstInstallTime ?: 0L,
                        lastUsedTime = usage?.lastTimeUsed ?: 0L,
                        usageMs = usage?.totalTimeInForeground ?: 0L,
                        applicationInfo = appInfo
                    )
                }
                .toList()

            runOnUiThread {
                allApps.clear()
                allApps.addAll(apps)
                progressBar.visibility = View.GONE
                applyFilterAndSort()
            }
        }.start()
    }

    private fun loadUsageStats(): Map<String, android.app.usage.UsageStats> {
        if (!hasUsageAccess()) return emptyMap()
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 7L * 24L * 60L * 60L * 1000L
        return try {
            manager.queryAndAggregateUsageStats(start, end)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun applyFilterAndSort() {
        if (!::adapter.isInitialized) return

        val query = if (::searchBox.isInitialized) searchBox.text.toString().trim().lowercase(Locale.getDefault()) else ""
        val selectedSort = if (::sortSpinner.isInitialized) sortSpinner.selectedItemPosition else 0

        val filtered = allApps.filter {
            query.isEmpty() ||
                it.name.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.getDefault()).contains(query)
        }.toMutableList()

        when (selectedSort) {
            0 -> filtered.sortByDescending { it.installTime }
            1 -> filtered.sortBy { it.installTime }
            2 -> filtered.sortByDescending { it.usageMs }
            3 -> filtered.sortBy { it.usageMs }
            4 -> filtered.sortByDescending { it.lastUsedTime }
            5 -> filtered.sortBy { it.name.lowercase(Locale.getDefault()) }
        }

        visibleApps.clear()
        visibleApps.addAll(filtered)
        adapter.notifyDataSetChanged()

        val usageSuffix = if (hasUsageAccess()) {
            "Usage: last 7 days"
        } else {
            "Usage access not granted"
        }
        statusText.text = "${visibleApps.size} apps  •  $usageSuffix"
    }

    private fun requestUninstall(item: AppItem) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:${item.packageName}")
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${item.packageName}")
            }
            startActivity(fallback)
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        override fun getCount(): Int = visibleApps.size
        override fun getItem(position: Int): AppItem = visibleApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = getItem(position)

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageDrawable(item.applicationInfo.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(10)
            })

            val textColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            val name = TextView(this@MainActivity).apply {
                text = item.name
                textSize = 17f
                setTextColor(Color.rgb(25, 25, 28))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textColumn.addView(name)

            val installDate = if (item.installTime > 0) dateFormat.format(Date(item.installTime)) else "Unknown"
            val details = TextView(this@MainActivity).apply {
                text = "Installed $installDate  •  ${formatUsage(item.usageMs)} used"
                textSize = 12f
                setTextColor(Color.rgb(100, 100, 105))
            }
            textColumn.addView(details)

            val packageLabel = TextView(this@MainActivity).apply {
                text = item.packageName
                textSize = 11f
                setTextColor(Color.rgb(130, 130, 135))
            }
            textColumn.addView(packageLabel)

            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val uninstall = Button(this@MainActivity).apply {
                text = "Uninstall"
                isAllCaps = false
                setOnClickListener { requestUninstall(item) }
            }
            row.addView(uninstall, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            })

            return row
        }
    }

    private fun formatUsage(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMinutes = ms / 60_000L
        if (totalMinutes < 60L) return "${maxOf(1L, totalMinutes)}m"
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
