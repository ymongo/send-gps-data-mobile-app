#!/bin/bash
# Mobile Android Build Pipeline Smoke Test
# This script verifies the complete build and deployment pipeline

set -e  # Exit on any error

echo "=========================================="
echo "Mobile Android Build Pipeline Smoke Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PACKAGE_NAME="com.mongoutils.sendgpsdata"
MAIN_ACTIVITY=".MainActivity"
APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"

# Step 1: Build web app
echo -e "${YELLOW}Step 1: Building web app...${NC}"
npm run build
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Web build successful${NC}"
else
    echo -e "${RED}✗ Web build failed${NC}"
    exit 1
fi
echo ""

# Step 2: Sync Capacitor
echo -e "${YELLOW}Step 2: Syncing Capacitor...${NC}"
npx cap sync
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Capacitor sync successful${NC}"
else
    echo -e "${RED}✗ Capacitor sync failed${NC}"
    exit 1
fi
echo ""

# Step 3: Build Android APK
echo -e "${YELLOW}Step 3: Building Android APK...${NC}"
cd android
./gradlew assembleDebug
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Android APK build successful${NC}"
else
    echo -e "${RED}✗ Android APK build failed${NC}"
    exit 1
fi
cd ..
echo ""

# Step 4: Check for connected device/emulator
echo -e "${YELLOW}Step 4: Checking for connected device...${NC}"
DEVICE_COUNT=$(adb devices | grep -E "device$|emulator" | wc -l)
if [ $DEVICE_COUNT -eq 0 ]; then
    echo -e "${RED}✗ No device or emulator connected${NC}"
    echo "Please start an emulator or connect a device"
    exit 1
fi
echo -e "${GREEN}✓ Device/emulator connected${NC}"
echo ""

# Step 5: Wait for device to be fully booted
echo -e "${YELLOW}Step 5: Waiting for device to be ready...${NC}"
adb wait-for-device
BOOT_COMPLETE=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
RETRY_COUNT=0
MAX_RETRIES=30

while [ "$BOOT_COMPLETE" != "1" ] && [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    echo "Waiting for boot to complete... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 2
    BOOT_COMPLETE=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ "$BOOT_COMPLETE" != "1" ]; then
    echo -e "${RED}✗ Device boot timeout${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Device ready${NC}"
echo ""

# Step 6: Install APK
echo -e "${YELLOW}Step 6: Installing APK...${NC}"
adb install -r $APK_PATH
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ APK installed successfully${NC}"
else
    echo -e "${RED}✗ APK installation failed${NC}"
    exit 1
fi
echo ""

# Step 7: Launch app
echo -e "${YELLOW}Step 7: Launching app...${NC}"
adb shell am start -n ${PACKAGE_NAME}/${MAIN_ACTIVITY}
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ App launched${NC}"
else
    echo -e "${RED}✗ App launch failed${NC}"
    exit 1
fi
echo ""

# Step 8: Verify app is running
echo -e "${YELLOW}Step 8: Verifying app is running...${NC}"
sleep 3  # Give app time to start
CURRENT_FOCUS=$(adb shell dumpsys window | grep mCurrentFocus)
if echo "$CURRENT_FOCUS" | grep -q "$PACKAGE_NAME"; then
    echo -e "${GREEN}✓ App is running and showing home screen${NC}"
    echo "Current focus: $CURRENT_FOCUS"
else
    echo -e "${RED}✗ App is not in focus${NC}"
    echo "Current focus: $CURRENT_FOCUS"
    exit 1
fi
echo ""

echo "=========================================="
echo -e "${GREEN}All tests passed! ✓${NC}"
echo "=========================================="
