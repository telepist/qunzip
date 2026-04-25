; Inno Setup Script for Quick Unzip
; Archive Extraction Utility
; https://github.com/telepist/qunzip

#define MyAppName "Quick Unzip"
#define MyAppVersion GetEnv('QUNZIP_VERSION')
#if MyAppVersion == ""
  #define MyAppVersion "1.0.0"
#endif
#define MyAppPublisher "Quick Unzip Project"
#define MyAppURL "https://github.com/telepist/qunzip"
; The GUI exe is what file associations and shortcuts launch — it has no
; console flash on double-click. The CLI exe (qunzip.exe) ships alongside
; for terminal use and admin operations like --register-associations.
#define MyAppGuiExeName "QuickUnzip.exe"
#define MyAppCliExeName "qunzip.exe"
#define MyAppDescription "Cross-platform archive extraction utility"

[Setup]
; NOTE: Generate a new GUID using PowerShell: [guid]::NewGuid()
AppId={{8F7A3B2C-4D5E-4A6B-9C8D-1E2F3A4B5C6D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
AppComments={#MyAppDescription}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=LICENSE.txt
InfoBeforeFile=README.txt
OutputDir=..\..\build\installer-output
OutputBaseFilename=quick-unzip-setup-{#MyAppVersion}
;SetupIconFile=icon.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
;UninstallDisplayIcon={app}\{#MyAppGuiExeName}
UninstallDisplayName={#MyAppName}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppDescription}
VersionInfoCopyright=Copyright (C) 2025 {#MyAppPublisher}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "fileassoc"; Description: "Register file associations for archives (.zip, .7z, .rar, etc.)"; GroupDescription: "File Associations:"; Flags: checkedonce
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Main executables and dependencies
Source: "..\..\build\installer-staging\windows\{#MyAppGuiExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\QuickUnzip.exe.manifest"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\{#MyAppCliExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\qunzip.exe.manifest"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\7z.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\7z.dll"; DestDir: "{app}"; Flags: ignoreversion

; Documentation
Source: "..\..\build\installer-staging\windows\License.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Icons]
; Start Menu and desktop shortcuts launch the GUI binary so users get the
; ImGui dialog rather than a console window.
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppGuiExeName}"; Comment: "Extract archive files"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppGuiExeName}"; Tasks: desktopicon

[Run]
; Register file associations after installation (if task selected).
; The CLI binary owns this admin operation — it writes the registry keys
; pointing at the GUI binary.
Filename: "{app}\{#MyAppCliExeName}"; Parameters: "--register-associations"; StatusMsg: "Registering file associations..."; Flags: runhidden waituntilterminated; Tasks: fileassoc

[UninstallRun]
; Unregister file associations before uninstall (CLI handles cleanup of
; both current QuickUnzip.* and legacy Qunzip.* ProgIDs).
Filename: "{app}\{#MyAppCliExeName}"; Parameters: "--unregister-associations"; RunOnceId: "UnregisterAssociations"; Flags: runhidden

[Registry]
; Fallback registry entries for file associations.
; Created even if --register-associations fails. The CLI binary creates
; them first, this is just a backup.
; Admin installs write to HKCR (HKLM\Software\Classes), per-user installs write to HKCU\Software\Classes.

; ---- Per-format ProgIDs (so each file type shows its own name in Explorer) ----
; ZIP
Root: HKCR; Subkey: "QuickUnzip.zip"; ValueType: string; ValueName: ""; ValueData: "ZIP Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.zip\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.zip\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.zip"; ValueType: string; ValueName: ""; ValueData: "ZIP Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.zip\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.zip\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; 7-Zip
Root: HKCR; Subkey: "QuickUnzip.7z"; ValueType: string; ValueName: ""; ValueData: "7-Zip Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.7z\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.7z\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.7z"; ValueType: string; ValueName: ""; ValueData: "7-Zip Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.7z\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.7z\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; RAR
Root: HKCR; Subkey: "QuickUnzip.rar"; ValueType: string; ValueName: ""; ValueData: "RAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.rar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.rar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.rar"; ValueType: string; ValueName: ""; ValueData: "RAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.rar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.rar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; TAR
Root: HKCR; Subkey: "QuickUnzip.tar"; ValueType: string; ValueName: ""; ValueData: "TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar"; ValueType: string; ValueName: ""; ValueData: "TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Compressed TAR formats
Root: HKCR; Subkey: "QuickUnzip.tar_gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tar_xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tar_xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Simple extensions for compressed TAR (Windows only sees the last extension)
Root: HKCR; Subkey: "QuickUnzip.gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Short TAR aliases (share ProgID with their full form)
Root: HKCR; Subkey: "QuickUnzip.tgz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tgz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tgz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tgz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tgz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tgz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tbz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tbz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.tbz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tbz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tbz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.tbz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.txz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.txz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.txz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.txz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.txz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.txz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Other archive formats
Root: HKCR; Subkey: "QuickUnzip.cab"; ValueType: string; ValueName: ""; ValueData: "Cabinet Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.cab\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.cab\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.cab"; ValueType: string; ValueName: ""; ValueData: "Cabinet Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.cab\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.cab\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.arj"; ValueType: string; ValueName: ""; ValueData: "ARJ Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.arj\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.arj\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.arj"; ValueType: string; ValueName: ""; ValueData: "ARJ Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.arj\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.arj\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.lzh"; ValueType: string; ValueName: ""; ValueData: "LZH Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.lzh\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "QuickUnzip.lzh\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.lzh"; ValueType: string; ValueName: ""; ValueData: "LZH Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.lzh\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppGuiExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\QuickUnzip.lzh\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppGuiExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode

; ---- Extension → ProgID mappings ----
Root: HKCR; Subkey: ".zip"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.zip"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".zip\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.zip"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.zip"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.zip"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".7z"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.7z"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".7z\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.7z"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.7z"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.7z"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".rar"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.rar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".rar\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.rar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.rar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.rar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".tar"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".tar.gz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.gz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.bz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.bz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.xz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.xz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.gz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.gz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.bz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.bz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.xz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tar_xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.xz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tar_xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".gz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".gz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.gz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.gz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".bz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".bz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.bz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.bz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".xz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".xz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.xz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.xz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".tgz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tgz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tgz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tgz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tbz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tbz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tbz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tbz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".txz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.txz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".txz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.txz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tgz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tgz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tgz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tgz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tbz2"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.tbz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tbz2\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.tbz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.txz"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.txz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.txz\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.txz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".cab"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.cab"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".cab\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.cab"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".arj"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.arj"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".arj\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.arj"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".lzh"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.lzh"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".lzh\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.lzh"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.cab"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.cab"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.cab\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.cab"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.arj"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.arj"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.arj\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.arj"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.lzh"; ValueType: string; ValueName: ""; ValueData: "QuickUnzip.lzh"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.lzh\OpenWithProgids"; ValueType: string; ValueName: "QuickUnzip.lzh"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    Log('Installation completed successfully');
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ProgIds: array of string;
  I: Integer;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // Fallback cleanup: remove any remaining ProgIDs (current + legacy
    // pre-rename "Qunzip.*" names). Runs after --unregister-associations.
    Log('Performing final cleanup');

    SetArrayLength(ProgIds, 34);
    // Current names
    ProgIds[0]  := 'QuickUnzip.zip';
    ProgIds[1]  := 'QuickUnzip.7z';
    ProgIds[2]  := 'QuickUnzip.rar';
    ProgIds[3]  := 'QuickUnzip.tar';
    ProgIds[4]  := 'QuickUnzip.tar_gz';
    ProgIds[5]  := 'QuickUnzip.tar_bz2';
    ProgIds[6]  := 'QuickUnzip.tar_xz';
    ProgIds[7]  := 'QuickUnzip.gz';
    ProgIds[8]  := 'QuickUnzip.bz2';
    ProgIds[9]  := 'QuickUnzip.xz';
    ProgIds[10] := 'QuickUnzip.tgz';
    ProgIds[11] := 'QuickUnzip.tbz2';
    ProgIds[12] := 'QuickUnzip.txz';
    ProgIds[13] := 'QuickUnzip.cab';
    ProgIds[14] := 'QuickUnzip.arj';
    ProgIds[15] := 'QuickUnzip.lzh';
    ProgIds[16] := 'QuickUnzip.ArchiveFile';
    // Legacy pre-rename names
    ProgIds[17] := 'Qunzip.zip';
    ProgIds[18] := 'Qunzip.7z';
    ProgIds[19] := 'Qunzip.rar';
    ProgIds[20] := 'Qunzip.tar';
    ProgIds[21] := 'Qunzip.tar_gz';
    ProgIds[22] := 'Qunzip.tar_bz2';
    ProgIds[23] := 'Qunzip.tar_xz';
    ProgIds[24] := 'Qunzip.gz';
    ProgIds[25] := 'Qunzip.bz2';
    ProgIds[26] := 'Qunzip.xz';
    ProgIds[27] := 'Qunzip.tgz';
    ProgIds[28] := 'Qunzip.tbz2';
    ProgIds[29] := 'Qunzip.txz';
    ProgIds[30] := 'Qunzip.cab';
    ProgIds[31] := 'Qunzip.arj';
    ProgIds[32] := 'Qunzip.lzh';
    ProgIds[33] := 'Qunzip.ArchiveFile';

    for I := 0 to GetArrayLength(ProgIds) - 1 do
    begin
      if IsAdminInstallMode then
        RegDeleteKeyIncludingSubkeys(HKEY_CLASSES_ROOT, ProgIds[I])
      else
        RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, 'Software\Classes\' + ProgIds[I]);
    end;
  end;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  Log('Initializing Quick Unzip installer version {#MyAppVersion}');
end;
