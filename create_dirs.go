package main

import (
	"fmt"
	"os"
	"path/filepath"
)

func main() {
	baseDir := `C:\Users\nguem\Documents\GitHub\investpro`
	dirs := []string{
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\registry`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\health`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\normalization`),
	}

	for _, dir := range dirs {
		err := os.MkdirAll(dir, 0755)
		if err != nil {
			fmt.Printf("Error creating %s: %v\n", dir, err)
		} else {
			fmt.Printf("Created: %s\n", dir)
		}
	}

	fmt.Println("All directories created successfully")
}
