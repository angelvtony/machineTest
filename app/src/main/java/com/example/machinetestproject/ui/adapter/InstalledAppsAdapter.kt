package com.example.machinetestproject.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.machinetestproject.R
import com.example.machinetestproject.ui.model.InstalledApp

class InstalledAppsAdapter(private val installedApps: List<InstalledApp>) : RecyclerView.Adapter<InstalledAppsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAppIcon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        val tvInstallTime: TextView = itemView.findViewById(R.id.tvInstallTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val installedApp = installedApps[position]
        holder.imgAppIcon.setImageDrawable(installedApp.appIcon)
        holder.tvAppName.text = installedApp.appName
        holder.tvPackageName.text = installedApp.packageName
        holder.tvInstallTime.text = installedApp.installTime
    }

    override fun getItemCount(): Int = installedApps.size
}
