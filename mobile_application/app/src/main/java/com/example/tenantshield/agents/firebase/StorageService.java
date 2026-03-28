package com.example.tenantshield.agents.firebase;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageService {

    private static final String TAG = "StorageService";
    private final FirebaseStorage storage;
    private final StorageReference storageRef;

    public interface UploadCallback {
        void onSuccess(List<String> downloadUrls);
        void onError(String message);
    }

    public interface SingleUploadCallback {
        void onSuccess(String downloadUrl);
        void onError(String message);
    }

    public StorageService() {
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
    }

    public void uploadInspectionImage(String userId, String inspectionId,
                                      String localFilePath, SingleUploadCallback callback) {
        File file = new File(localFilePath);
        Uri uri = Uri.fromFile(file);
        StorageReference ref = storageRef.child(
                "inspections/" + userId + "/" + inspectionId + "/" + file.getName());

        ref.putFile(uri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        ref.getDownloadUrl()
                                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri downloadUrl) {
                                        callback.onSuccess(downloadUrl.toString());
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception e) {
                                        Log.e(TAG, "Failed to get download URL", e);
                                        callback.onError(e.getMessage());
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to upload image", e);
                        callback.onError(e.getMessage());
                    }
                });
    }

    public void uploadMultipleImages(String userId, String inspectionId,
                                     List<String> localFilePaths, UploadCallback callback) {
        if (localFilePaths == null || localFilePaths.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        ArrayList<String> downloadUrls = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);
        int total = localFilePaths.size();

        for (String filePath : localFilePaths) {
            uploadInspectionImage(userId, inspectionId, filePath, new SingleUploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    synchronized (downloadUrls) {
                        downloadUrls.add(downloadUrl);
                    }
                    if (counter.incrementAndGet() == total) {
                        callback.onSuccess(downloadUrls);
                    }
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        }
    }

    public void deleteInspectionImages(String userId, String inspectionId) {
        StorageReference folderRef = storageRef.child(
                "inspections/" + userId + "/" + inspectionId + "/");

        folderRef.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference item : listResult.getItems()) {
                        item.delete()
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Failed to delete " + item.getPath(), e));
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to list items for deletion", e));
    }
}
