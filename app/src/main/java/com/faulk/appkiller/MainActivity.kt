package com.faulk.appkiller

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    var isSelected: Boolean = false
)

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.app_card)
        val icon: ImageView = itemView.findViewById(R.id.app_icon)
        val name: TextView = itemView.findViewById(R.id.app_name)
        val packageNameText: TextView = itemView.findViewById(R.id.app_package)
        val systemBadge: TextView = itemView.findViewById(R.id.system_badge)
        val checkbox: CheckBox = itemView.findViewById(R.id.app_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        holder.name.text = app.appName
        holder.packageNameText.text = app.packageName
        holder.icon.setImageDrawable(app.icon)
        holder.checkbox.isChecked = app.isSelected
        holder.systemBadge.visibility = if (app.isSystemApp) View.VISIBLE else View.GONE

        // Dim system apps slightly to indicate caution
        holder.card.alpha = if (app.isSystemApp) 0.75f else 1.0f

        holder.card.setOnClickListener {
            app.isSelected = !app.isSelected
            holder.checkbox.isChecked = app.isSelected
            onSelectionChanged(getSelectedCount())
        }

        holder.checkbox.setOnClickListener {
            app.isSelected = holder.checkbox.isChecked
            onSelectionChanged(getSelectedCount())
        }
    }

    override fun getItemCount(): Int = apps.size

    fun getSelectedApps(): List<AppInfo> = apps.filter { it.isSelected }

    fun getSelectedCount(): Int = apps.count { it.isSelected }

    fun selectAllUserApps() {
        apps.forEach { it.isSelected = !it.isSystemApp }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun deselectAll() {
        apps.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun updateList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var btnHibernate: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvTotalApps: TextView
    private lateinit var chipUser: Chip
    private lateinit var chipSystem: Chip
    private lateinit var chipAll: Chip
    private lateinit var switchShowSystem: SwitchMaterial
    private lateinit var emptyView: View

    private var allApps: List<AppInfo> = emptyList()
    private var showSystemApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupChipFilters()
        setupButtons()
        loadApps()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_apps)
        btnHibernate = findViewById(R.id.btn_hibernate)
        btnSelectAll = findViewById(R.id.btn_select_all)
        tvSelectedCount = findViewById(R.id.tv_selected_count)
        tvTotalApps = findViewById(R.id.tv_total_apps)
        chipUser = findViewById(R.id.chip_user)
        chipSystem = findViewById(R.id.chip_system)
        chipAll = findViewById(R.id.chip_all)
        emptyView = findViewById(R.id.empty_view)
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter(emptyList()) { selectedCount ->
            updateSelectedCount(selectedCount)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Add item animations
        recyclerView.itemAnimator?.apply {
            addDuration = 200
            removeDuration = 200
            changeDuration = 150
        }
    }

    private fun setupChipFilters() {
        chipUser.isChecked = true

        chipUser.setOnClickListener {
            chipUser.isChecked = true
            chipSystem.isChecked = false
            chipAll.isChecked = false
            showSystemApps = false
            filterApps(showOnlyUser = true)
        }

        chipSystem.setOnClickListener {
            chipUser.isChecked = false
            chipSystem.isChecked = true
            chipAll.isChecked = false
            showSystemApps = true
            filterApps(showOnlySystem = true)
        }

        chipAll.setOnClickListener {
            chipUser.isChecked = false
            chipSystem.isChecked = false
            chipAll.isChecked = true
            showSystemApps = true
            filterApps()
        }
    }

    private fun setupButtons() {
        btnHibernate.setOnClickListener {
            val selected = adapter.getSelectedApps()
            if (selected.isEmpty()) {
                Snackbar.make(
                    findViewById(R.id.root_layout),
                    "No apps selected",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Warn if system apps are selected
            val systemAppsSelected = selected.filter { it.isSystemApp }
            if (systemAppsSelected.isNotEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ System Apps Selected")
                    .setMessage(
                        "You have ${systemAppsSelected.size} system app(s) selected. " +
                        "Force-stopping system apps may cause instability.\n\n" +
                        "Do you want to continue?"
                    )
                    .setPositiveButton("Continue") { _, _ ->
                        hibernateApps(selected)
                    }
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Skip System Apps") { _, _ ->
                        hibernateApps(selected.filter { !it.isSystemApp })
                    }
                    .show()
            } else {
                hibernateApps(selected)
            }
        }

        btnSelectAll.setOnClickListener {
            if (adapter.getSelectedCount() > 0) {
                adapter.deselectAll()
                btnSelectAll.text = "Select All"
                btnSelectAll.setIconResource(R.drawable.ic_select_all)
            } else {
                adapter.selectAllUserApps() // FIX #1: Only selects USER apps, not system
                btnSelectAll.text = "Deselect All"
                btnSelectAll.setIconResource(R.drawable.ic_deselect)
            }
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val ownPackageName = packageName

        allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != ownPackageName } // Exclude self
            .map { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = try { appInfo.loadIcon(pm) } catch (e: Exception) { null },
                    isSystemApp = isSystem,
                    isSelected = false // FIX #1: Never auto-select anything
                )
            }
            .sortedWith(compareBy<AppInfo> { it.isSystemApp }.thenBy { it.appName.lowercase() })

        // Default: show only user apps
        filterApps(showOnlyUser = true)
    }

    private fun filterApps(showOnlyUser: Boolean = false, showOnlySystem: Boolean = false) {
        val filtered = when {
            showOnlyUser -> allApps.filter { !it.isSystemApp }
            showOnlySystem -> allApps.filter { it.isSystemApp }
            else -> allApps
        }

        adapter.updateList(filtered)
        tvTotalApps.text = "${filtered.size} apps"

        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateSelectedCount(count: Int) {
        tvSelectedCount.text = if (count > 0) "$count selected" else "None selected"
        btnHibernate.isEnabled = count > 0

        // Update button alpha for visual feedback
        btnHibernate.alpha = if (count > 0) 1.0f else 0.5f

        btnSelectAll.text = if (count > 0) "Deselect All" else "Select All"
        btnSelectAll.setIconResource(
            if (count > 0) R.drawable.ic_deselect else R.drawable.ic_select_all
        )
    }

    // FIX #2: Safe hibernate implementation that won't crash
    private fun hibernateApps(apps: List<AppInfo>) {
        if (apps.isEmpty()) {
            Snackbar.make(
                findViewById(R.id.root_layout),
                "No apps to hibernate",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        var successCount = 0
        var failCount = 0
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        if (activityManager == null) {
            Snackbar.make(
                findViewById(R.id.root_layout),
                "Unable to access system services",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        for (app in apps) {
            try {
                // killBackgroundProcesses is the safe, non-root approach
                // It requires android.permission.KILL_BACKGROUND_PROCESSES
                activityManager.killBackgroundProcesses(app.packageName)
                successCount++
            } catch (e: SecurityException) {
                failCount++
            } catch (e: Exception) {
                failCount++
            }
        }

        // Deselect all after operation
        adapter.deselectAll()

        // Show result
        val message = when {
            failCount == 0 -> "✓ Hibernated $successCount app(s)"
            successCount == 0 -> "Could not hibernate any apps. Check permissions."
            else -> "Hibernated $successCount app(s), $failCount failed"
        }

        Snackbar.make(
            findViewById(R.id.root_layout),
            message,
            Snackbar.LENGTH_LONG
        ).setAction("Details") {
            showPermissionHelp()
        }.show()
    }

    private fun showPermissionHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About Hibernation")
            .setMessage(
                "AppKiller uses Android's killBackgroundProcesses API to safely stop " +
                "background apps.\n\n" +
                "Note: Some apps may restart automatically. For persistent apps, " +
                "consider using Android's built-in battery optimization settings.\n\n" +
                "On Android 14+, some restrictions may prevent stopping certain apps."
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Battery Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            .show()
    }
}
