package main

import (
    "fmt"
    "os"
)

func main() {
    dirs := []string{
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\lifecycle`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\position`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\execution`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\ai`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\performance`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\management`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\pipeline`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\portfolio`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\strategy\persistence`,
        `C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\ui\panels`,
    }
    for _, d := range dirs {
        err := os.MkdirAll(d, 0755)
        if err != nil {
            fmt.Println("Error:", d, err)
        } else {
            fmt.Println("Created:", d)
        }
    }
}
