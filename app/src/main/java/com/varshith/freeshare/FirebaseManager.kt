package com.varshith.freeshare


import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.log
import kotlin.random.Random

class FirebaseManager {
    private val storage = Firebase.storage
    private val database = Firebase.database.reference

    suspend fun uploadFile(fileUri: Uri, fileName: String): Int {
        Log.d("FileName",fileName)
        val uniqueId = generateUniqueId()
        val fileExtension = getFileExtension(fileName)
        val storageFileName = "$uniqueId.$fileExtension"
        val storageRef = storage.reference.child("files/$storageFileName")

        return try {
            // Upload file to Firebase Storage
            storageRef.putFile(fileUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Save metadata to Realtime Database
            val fileMetadata = hashMapOf(
                "filename" to fileName,
                "url" to downloadUrl,
                "unique" to uniqueId
            )
            database.child("fileData").child(UUID.randomUUID().toString()).setValue(fileMetadata).await()

            uniqueId
        } catch (e: Exception) {
            throw Exception("File upload failed: ${e.message}")
        }
    }

    private suspend fun generateUniqueId(): Int {
        var randomNumber: Int
        do {
            randomNumber = generateRandomNumber()
        } while (checkRandomNumberAlreadyPresent(randomNumber))
        return randomNumber
    }

    private fun generateRandomNumber(): Int {
        return Random.nextInt(10000, 99999)
    }

    private suspend fun checkRandomNumberAlreadyPresent(randomNumber: Int): Boolean {
        val snapshot = database.child("fileData").child(randomNumber.toString()).get().await()
        return snapshot.exists()
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substring(fileName.lastIndexOf(".") + 1)
    }

    suspend fun getFileUrl(uniqueId: Int): String? {
        return try {
            val snapshot = database.child("fileData").get().await()
            var fileUrl: String? = null

            // Iterate through all children to find matching uniqueId
            snapshot.children.forEach { child ->
                val unique = child.child("unique").getValue(Int::class.java)
                if (unique == uniqueId) {
                    fileUrl = child.child("url").getValue(String::class.java)
                    return@forEach
                }
            }
            fileUrl
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getFileName(uniqueId: Int): String? {
        return try {
            val snapshot = database.child("fileData").get().await()
            var fileName: String? = null

            // Iterate through all children to find matching uniqueId
            snapshot.children.forEach { child ->
                val unique = child.child("unique").getValue(Int::class.java)
                if (unique == uniqueId) {
                    fileName = child.child("filename").getValue(String::class.java)
                    return@forEach
                }
            }
            fileName ?: "downloaded_file_$uniqueId"
        } catch (e: Exception) {
            "downloaded_file_$uniqueId"
        }
    }

    suspend fun getFileExtension(uniqueId: Int): String? {
        return try {
            val fileName = getFileName(uniqueId)
            fileName?.substring(fileName.lastIndexOf(".") + 1)
        } catch (e: Exception) {
            null
        }
    }

    fun listenToFile(uniqueId: Int, onUpdate: (String?) -> Unit) {
        database.child("fileData").child(uniqueId.toString()).child("url")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.getValue(String::class.java)
                    onUpdate(url)
                }

                override fun onCancelled(error: DatabaseError) {
                    onUpdate(null)
                }
            })
    }
}