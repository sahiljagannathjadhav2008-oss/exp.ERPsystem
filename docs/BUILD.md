# Build on GitHub

1. Create an empty GitHub repository.
2. Upload every file/folder from this archive to the repository root.
3. Commit to `main`.
4. Open **Actions**.
5. Run **Build Offline ERP APK** manually, or push another commit.
6. Wait for the `Analyze`, `Test`, and `Build release APK` steps to pass.
7. Open the completed workflow run and download the `offline-erp-release` artifact.
8. Extract the artifact and install `app-release.apk` on Android.

The workflow generates the Android host project automatically with `flutter create`, so the archive does not need to carry Flutter's generated Android boilerplate.
