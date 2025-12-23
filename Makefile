.PHONY: debug release clean uninstall

# Default target
debug:
	./gradlew assembleDebug
	adb install -r app/build/outputs/apk/debug/app-debug.apk

release:
	./gradlew assembleRelease
	adb install -r app/build/outputs/apk/release/app-release.apk

uninstall:
	adb uninstall com.gifboard

clean:
	./gradlew clean
