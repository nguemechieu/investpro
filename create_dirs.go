package main

import (
	"fmt"
	"os"
	"path/filepath"
)

func main() {
	baseDir := `C:\Users\nguem\Documents\GitHub\investpro`
	dirs := []string{
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\execution`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\routing`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\throttle`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\coordination`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\cache`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\blockchain`),
		filepath.Join(baseDir, `src\main\java\org\investpro\exchange\distributed`),
	}

	for _, dir := range dirs {
		err := os.MkdirAll(dir, 0755)
		if err != nil {
			fmt.Printf("✗ Error creating %s: %v\n", dir, err)
		} else {
			fmt.Printf("✓ Created: %s\n", dir)
		}
	}

	fmt.Println("\nVerifying directories...")
	for _, dir := range dirs {
		info, err := os.Stat(dir)
		if err == nil && info.IsDir() {
			fmt.Printf("✓ %s\n", dir)
		} else {
			fmt.Printf("✗ %s\n", dir)
		}
	}

	fmt.Println("\nAll directories created successfully")
}
