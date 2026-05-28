#!/usr/bin/env python3
import os
import sys

directories = [
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\execution',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\routing',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\throttle',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\coordination',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\cache',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\blockchain',
    r'C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\distributed'
]

created = []
failed = []

for directory in directories:
    try:
        os.makedirs(directory, exist_ok=True)
        created.append(directory)
        print(f"✓ Created: {directory}")
    except Exception as e:
        failed.append((directory, str(e)))
        print(f"✗ Failed: {directory} - {e}")

print(f"\n{len(created)} directories created successfully")
if failed:
    print(f"{len(failed)} directories failed:")
    for d, err in failed:
        print(f"  - {d}: {err}")
    sys.exit(1)
else:
    print("All directories created successfully!")
    # Verify
    print("\nVerification:")
    for directory in directories:
        exists = os.path.isdir(directory)
        print(f"  {'✓' if exists else '✗'} {directory}")
    sys.exit(0)
