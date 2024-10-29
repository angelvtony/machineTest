package com.example.machinetestproject.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.machinetestproject.coreextensions.ApkInstallationExtension
import com.example.machinetestproject.ui.model.InstalledApp
import com.example.machinetestproject.ui.adapter.InstalledAppsAdapter
import com.example.machinetestproject.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectFolder: Button
    private lateinit var btnSelectDateTime: Button
    private lateinit var btnSubmit: Button
    private lateinit var tvSelectedDateTime: TextView
    private lateinit var rvInstalledApps: RecyclerView
    private var selectedFolderUri: Uri? = null
    private var selectedInstallTime: Long? = null
    private val installedAppsList = mutableListOf<InstalledApp>()

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        btnSelectDateTime = findViewById(R.id.btnSelectDateTime)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvSelectedDateTime = findViewById(R.id.tvSelectedDateTime)
        rvInstalledApps = findViewById(R.id.rvInstalledApps)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupListeners()
        requestPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupListeners() {
        btnSelectFolder.setOnClickListener { openFolderPicker() }
        btnSelectDateTime.setOnClickListener { showDateTimePicker() }
        btnSubmit.setOnClickListener { onSubmit() }
    }

    private fun setupRecyclerView() {
        rvInstalledApps.layoutManager = LinearLayoutManager(this)
        rvInstalledApps.adapter = InstalledAppsAdapter(installedAppsList)
    }

    private fun onSubmit() {
        if (selectedFolderUri == null) {
            notifyUser("Please select a folder containing APK files.")
            return
        }
        if (selectedInstallTime == null) {
            notifyUser("Please select a date and time.")
            return
        }
        val apkFiles = getApkFilesInFolder(selectedFolderUri!!)
        if (apkFiles.isEmpty()) {
            notifyUser("No APK files found in the selected folder.")
            return
        }
        scheduleApkInstallation(apkFiles, selectedInstallTime!!)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_FOLDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FOLDER && resultCode == Activity.RESULT_OK) {
            val folderUri = data?.data ?: return
            selectedFolderUri = folderUri
            notifyUser("Folder selected successfully: $folderUri")

            contentResolver.takePersistableUriPermission(
                folderUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val apkFiles = getApkFilesInFolder(folderUri)
            if (apkFiles.isEmpty()) {
                notifyUser("No APK files found in the selected folder.")
            } else {
                notifyUser("${apkFiles.size} APK files found.")
                installApkFiles(apkFiles)
            }
        }
    }

    private fun installApkFiles(apkFiles: List<Uri>) {
        for (apkUri in apkFiles) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    private fun getApkFilesInFolder(folderUri: Uri): List<Uri> {
        val apkFiles = mutableListOf<Uri>()
        contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri)),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val mimeType = cursor.getString(1)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)

                Log.d("APKFileCheck", "Found file: $fileUri with MIME type: $mimeType")

                if (mimeType == "application/vnd.android.package-archive") {
                    apkFiles.add(fileUri)
                }
            }
        }
        return apkFiles
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(this, { _, year, month, day ->
            val timePicker = TimePickerDialog(this, { _, hour, minute ->
                val selectedTimeText = "$day/${month + 1}/$year $hour:$minute"
                tvSelectedDateTime.text = selectedTimeText
                selectedInstallTime = Calendar.getInstance().apply {
                    set(year, month, day, hour, minute)
                }.timeInMillis
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        datePicker.show()
    }

    private fun scheduleApkInstallation(apkFiles: List<Uri>, installationTime: Long) {
        if (installationTime <= System.currentTimeMillis()) {
            notifyUser("Selected time has already passed")
            return
        }
        val delay = installationTime - System.currentTimeMillis()

        val apkUris = apkFiles.map { it.toString() }
        val data = Data.Builder()
            .putStringArray("apkUris", apkUris.toTypedArray())
            .build()
        val workRequest = OneTimeWorkRequestBuilder<ApkInstallationExtension>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
        notifyUser("Installation scheduled successfully.")
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.REQUEST_INSTALL_PACKAGES),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyUser("Permission granted.")
            } else {
                notifyUser("Permission denied. Cannot access storage.")
            }
        }
    }


    private fun notifyUser(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_CODE_FOLDER = 1001
        private const val REQUEST_CODE_PERMISSIONS = 1002
    }
}