; Inno Setup Script for Qunzip
; Archive Extraction Utility
; https://github.com/telepist/qunzip

#define MyAppName "Qunzip"
#define MyAppVersion GetEnv('QUNZIP_VERSION')
#if MyAppVersion == ""
  #define MyAppVersion "1.0.0"
#endif
#define MyAppPublisher "Qunzip Project"
#define MyAppURL "https://github.com/telepist/qunzip"
#define MyAppExeName "qunzip.exe"
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
OutputBaseFilename=qunzip-setup-{#MyAppVersion}
;SetupIconFile=icon.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
;UninstallDisplayIcon={app}\{#MyAppExeName}
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
; Main executable and dependencies
Source: "..\..\build\installer-staging\windows\qunzip.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\7z.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\installer-staging\windows\7z.dll"; DestDir: "{app}"; Flags: ignoreversion

; Documentation
Source: "..\..\build\installer-staging\windows\License.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Icons]
; Start Menu shortcuts
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Comment: "Extract archive files"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"

; Desktop shortcut (optional)
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; Register file associations after installation (if task selected)
Filename: "{app}\{#MyAppExeName}"; Parameters: "--register-associations"; StatusMsg: "Registering file associations..."; Flags: runhidden waituntilterminated; Tasks: fileassoc

[UninstallRun]
; Unregister file associations before uninstall
Filename: "{app}\{#MyAppExeName}"; Parameters: "--unregister-associations"; RunOnceId: "UnregisterAssociations"; Flags: runhidden

[Registry]
; Fallback registry entries for file associations
; These will be created even if --register-associations fails
; The executable will try to create them first, this is just a backup
; Admin installs write to HKCR (HKLM\Software\Classes), per-user installs write to HKCU\Software\Classes

; Create per-format ProgIDs so each file type shows its own name in Explorer
; ZIP
Root: HKCR; Subkey: "Qunzip.zip"; ValueType: string; ValueName: ""; ValueData: "ZIP Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.zip\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.zip\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.zip"; ValueType: string; ValueName: ""; ValueData: "ZIP Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.zip\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.zip\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; 7-Zip
Root: HKCR; Subkey: "Qunzip.7z"; ValueType: string; ValueName: ""; ValueData: "7-Zip Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.7z\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.7z\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.7z"; ValueType: string; ValueName: ""; ValueData: "7-Zip Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.7z\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.7z\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; RAR
Root: HKCR; Subkey: "Qunzip.rar"; ValueType: string; ValueName: ""; ValueData: "RAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.rar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.rar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.rar"; ValueType: string; ValueName: ""; ValueData: "RAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.rar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.rar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; TAR
Root: HKCR; Subkey: "Qunzip.tar"; ValueType: string; ValueName: ""; ValueData: "TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar"; ValueType: string; ValueName: ""; ValueData: "TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Compressed TAR formats
Root: HKCR; Subkey: "Qunzip.tar_gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tar_xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tar_xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Simple extensions for compressed TAR (Windows only sees the last extension)
Root: HKCR; Subkey: "Qunzip.gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.gz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.gz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.gz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.bz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.bz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.bz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.xz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.xz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.xz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Short TAR aliases (share ProgID with their full form)
Root: HKCR; Subkey: "Qunzip.tgz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tgz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tgz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tgz"; ValueType: string; ValueName: ""; ValueData: "Gzipped TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tgz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tgz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tbz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tbz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.tbz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tbz2"; ValueType: string; ValueName: ""; ValueData: "Bzip2 TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tbz2\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.tbz2\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.txz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.txz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.txz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.txz"; ValueType: string; ValueName: ""; ValueData: "XZ TAR Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.txz\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.txz\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
; Other archive formats
Root: HKCR; Subkey: "Qunzip.cab"; ValueType: string; ValueName: ""; ValueData: "Cabinet Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.cab\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.cab\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.cab"; ValueType: string; ValueName: ""; ValueData: "Cabinet Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.cab\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.cab\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.arj"; ValueType: string; ValueName: ""; ValueData: "ARJ Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.arj\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.arj\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.arj"; ValueType: string; ValueName: ""; ValueData: "ARJ Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.arj\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.arj\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.lzh"; ValueType: string; ValueName: ""; ValueData: "LZH Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.lzh\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Qunzip.lzh\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.lzh"; ValueType: string; ValueName: ""; ValueData: "LZH Archive"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.lzh\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Qunzip.lzh\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode

; Associate file extensions with their per-format ProgIDs
; ZIP
Root: HKCR; Subkey: ".zip"; ValueType: string; ValueName: ""; ValueData: "Qunzip.zip"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".zip\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.zip"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip"; ValueType: string; ValueName: ""; ValueData: "Qunzip.zip"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.zip"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; 7-Zip
Root: HKCR; Subkey: ".7z"; ValueType: string; ValueName: ""; ValueData: "Qunzip.7z"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".7z\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.7z"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z"; ValueType: string; ValueName: ""; ValueData: "Qunzip.7z"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.7z"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; RAR
Root: HKCR; Subkey: ".rar"; ValueType: string; ValueName: ""; ValueData: "Qunzip.rar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".rar\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.rar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar"; ValueType: string; ValueName: ""; ValueData: "Qunzip.rar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.rar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; TAR
Root: HKCR; Subkey: ".tar"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.tar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.tar"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; Compressed TAR formats
Root: HKCR; Subkey: ".tar.gz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.bz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.xz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.gz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.bz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.xz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tar_xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; Simple extensions for compressed TAR (what Windows actually uses for .tar.gz etc.)
Root: HKCR; Subkey: ".gz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".gz\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.gz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.gz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.gz\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.gz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".bz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".bz2\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.bz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.bz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.bz2\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.bz2"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCR; Subkey: ".xz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".xz\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.xz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.xz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.xz\OpenWithProgids"; ValueType: string; ValueName: "Qunzip.xz"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; Short TAR format aliases
Root: HKCR; Subkey: ".tgz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tgz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tbz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tbz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".txz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.txz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tgz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tgz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tbz2"; ValueType: string; ValueName: ""; ValueData: "Qunzip.tbz2"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.txz"; ValueType: string; ValueName: ""; ValueData: "Qunzip.txz"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
; Other archive formats
Root: HKCR; Subkey: ".cab"; ValueType: string; ValueName: ""; ValueData: "Qunzip.cab"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".arj"; ValueType: string; ValueName: ""; ValueData: "Qunzip.arj"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".lzh"; ValueType: string; ValueName: ""; ValueData: "Qunzip.lzh"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.cab"; ValueType: string; ValueName: ""; ValueData: "Qunzip.cab"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.arj"; ValueType: string; ValueName: ""; ValueData: "Qunzip.arj"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.lzh"; ValueType: string; ValueName: ""; ValueData: "Qunzip.lzh"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    // Post-installation tasks can be added here
    Log('Installation completed successfully');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ProgIds: array of string;
  I: Integer;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // Fallback cleanup: Remove any remaining per-format ProgID keys
    // This runs after --unregister-associations
    Log('Performing final cleanup');

    SetArrayLength(ProgIds, 17);
    ProgIds[0] := 'Qunzip.zip';
    ProgIds[1] := 'Qunzip.7z';
    ProgIds[2] := 'Qunzip.rar';
    ProgIds[3] := 'Qunzip.tar';
    ProgIds[4] := 'Qunzip.tar_gz';
    ProgIds[5] := 'Qunzip.tar_bz2';
    ProgIds[6] := 'Qunzip.tar_xz';
    ProgIds[7] := 'Qunzip.gz';
    ProgIds[8] := 'Qunzip.bz2';
    ProgIds[9] := 'Qunzip.xz';
    ProgIds[10] := 'Qunzip.tgz';
    ProgIds[11] := 'Qunzip.tbz2';
    ProgIds[12] := 'Qunzip.txz';
    ProgIds[13] := 'Qunzip.cab';
    ProgIds[14] := 'Qunzip.arj';
    ProgIds[15] := 'Qunzip.lzh';
    ProgIds[16] := 'Qunzip.ArchiveFile'; // legacy single ProgID

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
  Log('Initializing Qunzip installer version {#MyAppVersion}');
end;
