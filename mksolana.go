package main

import (
	"fmt"
	"os"
)

func main() {
	dir := `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\solana`
	err := os.MkdirAll(dir, 0755)
	if err != nil {
		fmt.Println("ERROR:", err)
		os.Exit(1)
	}
	fmt.Println("Created:", dir)
}
