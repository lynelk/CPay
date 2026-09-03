#!/usr/bin/env python3
"""Apply permanent Cito Business platform identifiers and store settings."""
from __future__ import annotations

import plistlib
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = "net.citotech.cito.business"
DISPLAY_NAME = "Cito Business"
URL_SCHEME = "cito-business"


def patch_android() -> None:
    gradle = ROOT / "android/app/build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    text = re.sub(r'namespace\s*=\s*"[^"]+"', f'namespace = "{APPLICATION_ID}"', text)
    text = re.sub(r'applicationId\s*=\s*"[^"]+"', f'applicationId = "{APPLICATION_ID}"', text)
    text = text.replace("compileSdk = flutter.compileSdkVersion", "compileSdk = 36")
    text = text.replace("minSdk = flutter.minSdkVersion", "minSdk = 24")
    text = text.replace("targetSdk = flutter.targetSdkVersion", "targetSdk = 36")

    imports = "import java.io.FileInputStream\nimport java.util.Properties\n\n"
    if "import java.util.Properties" not in text:
        text = imports + text

    properties_block = '''
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

'''
    if "val keystoreProperties = Properties()" not in text:
        text = text.replace("\nandroid {", f"\n{properties_block}android {{", 1)

    signing_block = '''
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

'''
    if "signingConfigs {" not in text:
        text = text.replace("    buildTypes {", signing_block + "    buildTypes {", 1)

    text = text.replace(
        'signingConfig = signingConfigs.getByName("debug")',
        'signingConfig = if (keystorePropertiesFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")',
    )
    gradle.write_text(text, encoding="utf-8")

    kotlin_root = ROOT / "android/app/src/main/kotlin"
    activities = list(kotlin_root.rglob("MainActivity.kt"))
    if not activities:
        raise FileNotFoundError("Flutter did not generate MainActivity.kt")
    source = activities[0]
    activity_text = source.read_text(encoding="utf-8")
    activity_text = re.sub(r"^package\s+[^\n]+", f"package {APPLICATION_ID}", activity_text, flags=re.MULTILINE)
    destination = kotlin_root / Path(*APPLICATION_ID.split(".")) / "MainActivity.kt"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(activity_text, encoding="utf-8")
    if source.resolve() != destination.resolve():
        source.unlink()
        parent = source.parent
        while parent != kotlin_root and not any(parent.iterdir()):
            parent.rmdir()
            parent = parent.parent

    manifest = ROOT / "android/app/src/main/AndroidManifest.xml"
    manifest_text = manifest.read_text(encoding="utf-8")
    if "android.permission.INTERNET" not in manifest_text:
        manifest_text = manifest_text.replace(
            ">\n    <application",
            '>\n    <uses-permission android:name="android.permission.INTERNET" />\n    <application',
            1,
        )
    manifest_text = re.sub(r'android:label="[^"]*"', f'android:label="{DISPLAY_NAME}"', manifest_text, count=1)
    if f'android:scheme="{URL_SCHEME}"' not in manifest_text:
        intent_filter = f'''
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="{URL_SCHEME}" />
            </intent-filter>
'''
        manifest_text = manifest_text.replace("        </activity>", intent_filter + "        </activity>", 1)
    manifest.write_text(manifest_text, encoding="utf-8")


def add_privacy_manifest_to_project(pbxproj: Path) -> None:
    text = pbxproj.read_text(encoding="utf-8")
    if "PrivacyInfo.xcprivacy in Resources" in text:
        return

    build_id = "C17000000000000000000001"
    file_id = "C17000000000000000000002"
    build_line = f'\t\t{build_id} /* PrivacyInfo.xcprivacy in Resources */ = {{isa = PBXBuildFile; fileRef = {file_id} /* PrivacyInfo.xcprivacy */; }};\n'
    file_line = f'\t\t{file_id} /* PrivacyInfo.xcprivacy */ = {{isa = PBXFileReference; lastKnownFileType = text.xml; path = PrivacyInfo.xcprivacy; sourceTree = "<group>"; }};\n'

    text = text.replace("/* End PBXBuildFile section */", build_line + "/* End PBXBuildFile section */", 1)
    text = text.replace("/* End PBXFileReference section */", file_line + "/* End PBXFileReference section */", 1)

    runner_group = re.search(r"(/\* Runner \*/ = \{\s*isa = PBXGroup;\s*children = \(\n)", text)
    if runner_group:
        insertion = runner_group.group(1) + f"\t\t\t\t{file_id} /* PrivacyInfo.xcprivacy */,\n"
        text = text[: runner_group.start()] + insertion + text[runner_group.end() :]

    resources = re.search(r"(/\* Resources \*/ = \{\s*isa = PBXResourcesBuildPhase;\s*buildActionMask = \d+;\s*files = \(\n)", text)
    if resources:
        insertion = resources.group(1) + f"\t\t\t\t{build_id} /* PrivacyInfo.xcprivacy in Resources */,\n"
        text = text[: resources.start()] + insertion + text[resources.end() :]

    pbxproj.write_text(text, encoding="utf-8")


def patch_ios() -> None:
    pbxproj = ROOT / "ios/Runner.xcodeproj/project.pbxproj"
    text = pbxproj.read_text(encoding="utf-8")

    def bundle_identifier(match: re.Match[str]) -> str:
        existing = match.group(1).strip()
        suffix = ".RunnerTests" if existing.endswith(".RunnerTests") else ""
        return f"PRODUCT_BUNDLE_IDENTIFIER = {APPLICATION_ID}{suffix};"

    text = re.sub(
        r"PRODUCT_BUNDLE_IDENTIFIER = ([^;]+);",
        bundle_identifier,
        text,
    )
    text = re.sub(r"IPHONEOS_DEPLOYMENT_TARGET = [^;]+;", "IPHONEOS_DEPLOYMENT_TARGET = 15.0;", text)
    pbxproj.write_text(text, encoding="utf-8")

    info_path = ROOT / "ios/Runner/Info.plist"
    with info_path.open("rb") as stream:
        info = plistlib.load(stream)
    info["CFBundleDisplayName"] = DISPLAY_NAME
    info["CFBundleName"] = DISPLAY_NAME
    info["ITSAppUsesNonExemptEncryption"] = False
    info["CFBundleURLTypes"] = [
        {
            "CFBundleTypeRole": "Editor",
            "CFBundleURLName": APPLICATION_ID,
            "CFBundleURLSchemes": [URL_SCHEME],
        }
    ]
    info["UISupportedInterfaceOrientations"] = ["UIInterfaceOrientationPortrait"]
    info["UISupportedInterfaceOrientations~ipad"] = [
        "UIInterfaceOrientationPortrait",
        "UIInterfaceOrientationPortraitUpsideDown",
        "UIInterfaceOrientationLandscapeLeft",
        "UIInterfaceOrientationLandscapeRight",
    ]
    with info_path.open("wb") as stream:
        plistlib.dump(info, stream, sort_keys=False)

    privacy_path = ROOT / "ios/Runner/PrivacyInfo.xcprivacy"
    privacy = {
        "NSPrivacyTracking": False,
        "NSPrivacyTrackingDomains": [],
        "NSPrivacyCollectedDataTypes": [],
        "NSPrivacyAccessedAPITypes": [],
    }
    with privacy_path.open("wb") as stream:
        plistlib.dump(privacy, stream, sort_keys=False)
    add_privacy_manifest_to_project(pbxproj)


def copy_brand_asset() -> None:
    asset = ROOT / "assets/cito_icon.png"
    asset.parent.mkdir(parents=True, exist_ok=True)
    source = ROOT.parent / "clientside/public/logo512.png"
    if not source.exists():
        raise FileNotFoundError(f"Cito brand asset not found: {source}")
    shutil.copy2(source, asset)


def main() -> None:
    copy_brand_asset()
    patch_android()
    patch_ios()
    print(f"Configured Cito Business: {APPLICATION_ID}")


if __name__ == "__main__":
    main()
