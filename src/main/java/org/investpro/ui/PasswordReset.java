package org.investpro.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Password reset dialog for user account recovery.
 * Allows users to reset their password by verifying their username and email.
 */
public class PasswordReset extends Stage {
    private static final Logger logger = LoggerFactory.getLogger(PasswordReset.class);

    private TextField usernameField;
    private TextField emailField;
    private PasswordField newPasswordField;
    private PasswordField confirmPasswordField;
    private Label statusLabel;

    public PasswordReset() {
        setTitle("Reset Password");
        setWidth(400);
        setHeight(380);
        setResizable(false);

        GridPane gridPane = createFormLayout();
        
        Scene scene = new Scene(gridPane);
        scene.getStylesheets().add("/app.css");
        setScene(scene);

        show();
    }

    private GridPane createFormLayout() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(12);
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setStyle("-fx-padding: 20; -fx-background-color: #0f172a;");

        // Title
        Label titleLabel = new Label("Reset Your Password");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #cbd5e1;");
        gridPane.add(titleLabel, 0, 0, 2, 1);

        // Username field
        Label usernameLabel = new Label("Username:");
        usernameLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        usernameField = new TextField();
        usernameField.setStyle("-fx-font-size: 12px; -fx-padding: 6;");
        gridPane.add(usernameLabel, 0, 1);
        gridPane.add(usernameField, 1, 1);

        // Email field
        Label emailLabel = new Label("Email:");
        emailLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        emailField = new TextField();
        emailField.setStyle("-fx-font-size: 12px; -fx-padding: 6;");
        gridPane.add(emailLabel, 0, 2);
        gridPane.add(emailField, 1, 2);

        // New Password field
        Label newPasswordLabel = new Label("New Password:");
        newPasswordLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        newPasswordField = new PasswordField();
        newPasswordField.setStyle("-fx-font-size: 12px; -fx-padding: 6;");
        gridPane.add(newPasswordLabel, 0, 3);
        gridPane.add(newPasswordField, 1, 3);

        // Confirm Password field
        Label confirmPasswordLabel = new Label("Confirm Password:");
        confirmPasswordLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setStyle("-fx-font-size: 12px; -fx-padding: 6;");
        gridPane.add(confirmPasswordLabel, 0, 4);
        gridPane.add(confirmPasswordField, 1, 4);

        // Status label
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        gridPane.add(statusLabel, 0, 5, 2, 1);

        // Buttons
        Button resetButton = new Button("Reset Password");
        resetButton.setStyle("-fx-font-size: 12px; -fx-padding: 8 20;");
        resetButton.setOnAction(_ -> handlePasswordReset());

        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-font-size: 12px; -fx-padding: 8 20;");
        cancelButton.setOnAction(_ -> close());

        gridPane.add(resetButton, 0, 6);
        gridPane.add(cancelButton, 1, 6);

        return gridPane;
    }

    private void handlePasswordReset() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (username.isEmpty()) {
            showError("Please enter your username");
            return;
        }

        if (email.isEmpty()) {
            showError("Please enter your email");
            return;
        }

        if (!isValidEmail(email)) {
            showError("Please enter a valid email address");
            return;
        }

        if (newPassword.isEmpty()) {
            showError("Please enter a new password");
            return;
        }

        if (newPassword.length() < 6) {
            showError("Password must be at least 6 characters long");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        // Attempt password reset
        boolean success = performPasswordReset(username, email, newPassword);

        if (success) {
            showSuccess("Password reset successful! You can now login with your new password.");
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            javafx.application.Platform.runLater(PasswordReset.this::close);
                        }
                    },
                    2000
            );
        } else {
            showError("Password reset failed. Please check your username and email.");
        }
    }

    /**
     * Performs the actual password reset logic
     * In a production system, this would verify against a database
     */
    private boolean performPasswordReset(String username, String email, String newPassword) {
        try {
            // Get database connection from Db1
            Properties dbConfig = new Properties();
            org.investpro.data.Db1 db = new org.investpro.data.Db1(dbConfig);
            
            // Step 1: Verify that the username and email match in the database
            boolean userExists = verifyUserExists(db, username, email);
            
            if (!userExists) {
                logger.warn("Password reset attempt failed: username '{}' and email '{}' do not match", username, email);
                return false;
            }
            
            // Step 2: Hash the new password for security
            String hashedPassword = hashPassword(newPassword);
            
            // Step 3: Update the password in the database
            boolean updateSuccess = updateUserPassword(db, username, hashedPassword);
            
            if (!updateSuccess) {
                logger.error("Failed to update password in database for username: {}", username);
                return false;
            }
            
            // Step 4: Log the successful password reset
            logger.info("Password reset successful for username: {}", username);
            
            // Step 5: Send confirmation (placeholder for email notification)
            sendPasswordResetConfirmation(email);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error during password reset for username: {}", username, e);
            return false;
        }
    }
    
    /**
     * Verify that the username and email match a record in the database.
     */
    private boolean verifyUserExists(org.investpro.data.Db1 db, String username, String email) {
        try {
            String query = "SELECT COUNT(*) as count FROM users WHERE username = ? AND email = ?";
            java.sql.PreparedStatement stmt = db.getConnection().prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, email);
            
            java.sql.ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("count");
                return count > 0;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error verifying user existence for username: {}", username, e);
            return false;
        }
    }
    
    /**
     * Update the user's password in the database.
     */
    private boolean updateUserPassword(org.investpro.data.Db1 db, String username, String hashedPassword) {
        try {
            String query = "UPDATE users SET password = ? WHERE username = ?";
            java.sql.PreparedStatement stmt = db.getConnection().prepareStatement(query);
            stmt.setString(1, hashedPassword);
            stmt.setString(2, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (Exception e) {
            logger.error("Error updating password for username: {}", username, e);
            return false;
        }
    }
    
    /**
     * Hash a password using a simple mechanism.
     * Note: In production, use bcrypt, scrypt, or PBKDF2 instead.
     */
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("Error hashing password", e);
            // Fallback: return the password as-is (not ideal, but prevents complete failure)
            return password;
        }
    }
    
    /**
     * Send password reset confirmation email (placeholder).
     */
    private void sendPasswordResetConfirmation(String email) {
        try {
            // Placeholder for email notification logic
            // In production, integrate with an email service like JavaMail or SendGrid
            logger.info("Password reset confirmation would be sent to: {}", email);
            
            // Example using EmailNotifier if available:
            // org.investpro.core.EmailNotifier emailNotifier = new org.investpro.core.EmailNotifier();
            // emailNotifier.sendEmail(email, "Password Reset Successful", 
            //     "Your password has been successfully reset. You can now log in with your new password.");
            
        } catch (Exception e) {
            logger.warn("Could not send password reset confirmation email to: {}", email, e);
            // Don't fail the password reset just because email couldn't be sent
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11px;");
    }
}
