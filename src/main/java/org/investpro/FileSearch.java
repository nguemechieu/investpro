package org.investpro;

import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FileSearch {


    public static @Nullable File searchFile(String folderPath, String fileName) {
        // Create a File object for the folder
        File folder = new File(folderPath);

        // Get all files in the folder
        File[] files = folder.listFiles();

        // Check each file for the specified file name
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equals(fileName)) {
                    return file; // Found the file, return it
                }
            }
        }

        // File not found, return null
        return null;
    }
}