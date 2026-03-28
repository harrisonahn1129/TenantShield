package com.example.tenantshield.agents.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import android.util.Log;

public class FirebaseAuthService {

    private static final String TAG = "FirebaseAuthService";
    private final FirebaseAuth auth;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public FirebaseAuthService() {
        auth = FirebaseAuth.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    public String getUid() {
        FirebaseUser user = getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void signUpWithEmail(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signUpWithEmail: success");
                            callback.onSuccess(auth.getCurrentUser());
                        } else {
                            Log.w(TAG, "signUpWithEmail: failure", task.getException());
                            callback.onError(task.getException().getMessage());
                        }
                    }
                });
    }

    public void signInWithEmail(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithEmail: success");
                            callback.onSuccess(auth.getCurrentUser());
                        } else {
                            Log.w(TAG, "signInWithEmail: failure", task.getException());
                            callback.onError(task.getException().getMessage());
                        }
                    }
                });
    }

    public void signOut() {
        auth.signOut();
    }

    public void resetPassword(String email, AuthCallback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "resetPassword: email sent");
                            callback.onSuccess(null);
                        } else {
                            Log.w(TAG, "resetPassword: failure", task.getException());
                            callback.onError(task.getException().getMessage());
                        }
                    }
                });
    }
}
