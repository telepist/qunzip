; Inno Setup Script for Gunzip
; Archive Extraction Utility
; https://github.com/yourusername/gunzip

#define MyAppName "Gunzip"
#define MyAppVersion GetEnv('GUNZIP_VERSION')
#if MyAppVersion == ""
  #define MyAppVersion "1.0.0"
#endif
#define MyAppPublisher "Gunzip Project"
#define MyAppURL "https://github.com/yourusername/gunzip"
#define MyAppExeName "gunzip.exe"
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
OutputBaseFilename=gunzip-setup-{#MyAppVersion}
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
Source: "..\..\build\installer-staging\windows\gunzip.exe"; DestDir: "{app}"; Flags: ignoreversion
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

; Create ProgID for Gunzip
Root: HKCR; Subkey: "Gunzip.ArchiveFile"; ValueType: string; ValueName: ""; ValueData: "Archive File"; Flags: uninsdeletekey; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Gunzip.ArchiveFile\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: "Gunzip.ArchiveFile\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Gunzip.ArchiveFile"; ValueType: string; ValueName: ""; ValueData: "Archive File"; Flags: uninsdeletekey; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Gunzip.ArchiveFile\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\Gunzip.ArchiveFile\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: fileassoc; Check: not IsAdminInstallMode

; Associate file extensions with ProgID
; ZIP format
Root: HKCR; Subkey: ".zip"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".zip\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.zip\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; 7-Zip format
Root: HKCR; Subkey: ".7z"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".7z\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.7z\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; RAR format
Root: HKCR; Subkey: ".rar"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".rar\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.rar\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; TAR format
Root: HKCR; Subkey: ".tar"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar\OpenWithProgids"; ValueType: string; ValueName: "Gunzip.ArchiveFile"; ValueData: ""; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; Compressed TAR formats
Root: HKCR; Subkey: ".tar.gz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.bz2"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tar.xz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.gz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.bz2"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tar.xz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; Short TAR format aliases
Root: HKCR; Subkey: ".tgz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".tbz2"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".txz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tgz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.tbz2"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.txz"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

; Other archive formats
Root: HKCR; Subkey: ".cab"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".arj"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCR; Subkey: ".lzh"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.cab"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.arj"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode
Root: HKCU; Subkey: "Software\Classes\.lzh"; ValueType: string; ValueName: ""; ValueData: "Gunzip.ArchiveFile"; Flags: uninsdeletevalue; Tasks: fileassoc; Check: not IsAdminInstallMode

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
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // Fallback cleanup: Remove any remaining registry keys
    // This runs after --unregister-associations
    Log('Performing final cleanup');

    // Note: The --unregister-associations command should have already
    // cleaned up the registry. This is just a safety measure.
    if IsAdminInstallMode then
      RegDeleteKeyIncludingSubkeys(HKEY_CLASSES_ROOT, 'Gunzip.ArchiveFile')
    else
      RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, 'Software\Classes\Gunzip.ArchiveFile');
  end;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  Log('Initializing Gunzip installer version {#MyAppVersion}');
end;
