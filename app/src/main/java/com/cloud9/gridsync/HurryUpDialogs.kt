package com.cloud9.gridsync

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.cloud9.gridsync.database.HurryUpRepository
import com.cloud9.gridsync.network.SessionLogManager
import kotlin.concurrent.thread

// Dialogs shared by the Hurry-Up screens and the Play Library. Both stay open when the input is
// invalid, so the coach can fix it instead of starting over.
object HurryUpDialogs {

    fun showPackageNameDialog(
        activity: Activity,
        title: String,
        initialName: String,
        positiveLabel: String,
        excludePackageId: Long = 0,
        onValidName: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            hint = "Package Name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            setText(initialName)
            setSelection(text.length)
        }

        val container = FrameLayout(activity).apply {
            val padding = (20 * activity.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positiveLabel, null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                thread {
                    val taken = name.isNotBlank() &&
                        HurryUpRepository.isNameTaken(activity, name, excludePackageId)
                    val error = HurryUpRepository.validatePackageName(name, taken)
                    activity.runOnUiThread {
                        if (error != null) {
                            input.error = error
                        } else {
                            dialog.dismiss()
                            onValidName(name)
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    // labels can differ from values, for example to mark plays already in the package.
    fun showPlayPickerDialog(
        activity: Activity,
        title: String,
        playNames: List<String>,
        labels: List<String> = playNames,
        preselected: Set<String> = emptySet(),
        positiveLabel: String,
        minimumSelection: Int,
        minimumMessage: String,
        onConfirm: (List<String>) -> Unit
    ) {
        if (playNames.isEmpty()) {
            Toast.makeText(activity, "The Play Library has no saved plays yet", Toast.LENGTH_LONG).show()
            return
        }

        val checked = BooleanArray(playNames.size) { playNames[it] in preselected }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(positiveLabel, null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = playNames.filterIndexed { index, _ -> checked[index] }
                if (selected.size < minimumSelection) {
                    Toast.makeText(activity, minimumMessage, Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    onConfirm(selected)
                }
            }
        }

        dialog.show()
    }

    // New package flow: custom name, then at least two saved plays from the Play Library.
    fun startCreatePackageFlow(
        activity: Activity,
        preselectedPlays: Set<String> = emptySet(),
        onCreated: (packageId: Long, packageName: String) -> Unit
    ) {
        showPackageNameDialog(activity, "New Hurry-Up Package", "", "Next") { name ->
            thread {
                val libraryPlays = HurryUpRepository.getLibraryPlayNames(activity)
                activity.runOnUiThread {
                    if (libraryPlays.size < HurryUpRepository.MIN_PLAYS) {
                        Toast.makeText(
                            activity,
                            "Save at least ${HurryUpRepository.MIN_PLAYS} plays in the Play Library first",
                            Toast.LENGTH_LONG
                        ).show()
                        return@runOnUiThread
                    }

                    showPlayPickerDialog(
                        activity = activity,
                        title = "Select Saved Plays for \"$name\"",
                        playNames = libraryPlays,
                        preselected = preselectedPlays,
                        positiveLabel = "Create",
                        minimumSelection = HurryUpRepository.MIN_PLAYS,
                        minimumMessage = HurryUpRepository.MIN_PLAYS_MESSAGE
                    ) { selected ->
                        thread {
                            val packageId = HurryUpRepository.createPackage(activity, name, selected)
                            activity.runOnUiThread {
                                if (packageId == null) {
                                    Toast.makeText(activity, "Could not create the package", Toast.LENGTH_LONG).show()
                                } else {
                                    SessionLogManager.addEntry("Hurry-Up Package \"$name\" created")
                                    Toast.makeText(activity, "Created \"$name\"", Toast.LENGTH_SHORT).show()
                                    onCreated(packageId, name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun confirmDeletePackage(activity: Activity, packageId: Long, packageName: String, onDeleted: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Delete Package \"$packageName\"?")
            .setMessage("The package is removed. Its plays stay in the Play Library.")
            .setPositiveButton("Delete") { _, _ ->
                thread {
                    HurryUpRepository.deletePackage(activity, packageId)
                    activity.runOnUiThread {
                        SessionLogManager.addEntry("Hurry-Up Package \"$packageName\" deleted")
                        onDeleted()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun renamePackage(activity: Activity, packageId: Long, currentName: String, onRenamed: (String) -> Unit) {
        showPackageNameDialog(activity, "Rename Package", currentName, "Rename", packageId) { newName ->
            if (newName == currentName) return@showPackageNameDialog
            thread {
                val error = HurryUpRepository.renamePackage(activity, packageId, newName)
                activity.runOnUiThread {
                    if (error != null) {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                    } else {
                        SessionLogManager.addEntry("Package \"$currentName\" renamed to \"$newName\"")
                        onRenamed(newName)
                    }
                }
            }
        }
    }
}
