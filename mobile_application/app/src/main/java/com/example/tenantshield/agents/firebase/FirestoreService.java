package com.example.tenantshield.agents.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import android.util.Log;

import com.example.tenantshield.agents.models.UserInfo;
import com.example.tenantshield.agents.models.InspectionResult;
import com.example.tenantshield.agents.models.InspectionFinding;
import com.example.tenantshield.agents.models.ComplaintForm;

public class FirestoreService {

    private static final String TAG = "FirestoreService";
    private final FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";
    private static final String INSPECTIONS_COLLECTION = "inspections";

    public interface FirestoreCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public FirestoreService() {
        db = FirebaseFirestore.getInstance();
    }

    public void saveUserProfile(String userId, UserInfo userInfo, FirestoreCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("tenant_name", userInfo.getTenantName());
        data.put("address", userInfo.getAddress());
        data.put("unit_number", userInfo.getUnitNumber());
        data.put("complaint_description", userInfo.getComplaintDescription());
        data.put("inspection_request", userInfo.getInspectionRequest());
        data.put("updated_at", System.currentTimeMillis());

        db.collection(USERS_COLLECTION).document(userId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile saved successfully");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user profile", e);
                    callback.onError(e.getMessage());
                });
    }

    public void getUserProfile(String userId, DataCallback<Map<String, Object>> callback) {
        db.collection(USERS_COLLECTION).document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        callback.onSuccess(documentSnapshot.getData());
                    } else {
                        callback.onError("User profile not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user profile", e);
                    callback.onError(e.getMessage());
                });
    }

    public void saveInspectionRecord(String userId, InspectionResult result, ComplaintForm form,
                                     List<String> imageUrls, FirestoreCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("hazard_level", result.getHazardLevel().toString());
        data.put("overall_severity", result.getOverallSeverity());
        data.put("raw_analysis", result.getRawAnalysis());
        data.put("inspected_at", result.getInspectedAt());

        List<Map<String, Object>> findingsList = new ArrayList<>();
        for (InspectionFinding finding : result.getFindings()) {
            Map<String, Object> findingMap = new HashMap<>();
            findingMap.put("category", finding.getCategory());
            findingMap.put("description", finding.getDescription());
            findingMap.put("severity", finding.getSeverity().toString());
            findingMap.put("location", finding.getLocation());
            findingMap.put("evidence", finding.getEvidence());
            findingsList.add(findingMap);
        }
        data.put("findings", findingsList);

        data.put("recommended_actions", result.getRecommendedActions());
        data.put("image_urls", imageUrls);

        if (form != null) {
            data.put("document_id", form.getDocumentId());
            data.put("filing_date", form.getFilingDate());
            data.put("tenant_name", form.getTenantName());
            data.put("address", form.getAddress());
            data.put("nature_of_complaint", form.getNatureOfComplaint());
            data.put("hazard_class", form.getHazardClass());
            data.put("inspector_signature", form.getInspectorSignature());
        }

        data.put("created_at", System.currentTimeMillis());

        db.collection(USERS_COLLECTION).document(userId)
                .collection(INSPECTIONS_COLLECTION)
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Inspection record saved with ID: " + documentReference.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving inspection record", e);
                    callback.onError(e.getMessage());
                });
    }

    public void getInspectionHistory(String userId, DataCallback<List<Map<String, Object>>> callback) {
        db.collection(USERS_COLLECTION).document(userId)
                .collection(INSPECTIONS_COLLECTION)
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Map<String, Object>> inspections = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        Map<String, Object> data = new HashMap<>(document.getData());
                        data.put("_id", document.getId());
                        inspections.add(data);
                    }
                    callback.onSuccess(inspections);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting inspection history", e);
                    callback.onError(e.getMessage());
                });
    }

    public void deleteInspectionRecord(String userId, String inspectionId, FirestoreCallback callback) {
        db.collection(USERS_COLLECTION).document(userId)
                .collection(INSPECTIONS_COLLECTION)
                .document(inspectionId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Inspection record deleted successfully");
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting inspection record", e);
                    callback.onError(e.getMessage());
                });
    }
}
