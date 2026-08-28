#ifndef MyAppName
  #define MyAppName "IntelliDo"
#endif
#ifndef MyAppVersion
  #define MyAppVersion "0.1.0"
#endif
#ifndef MyAppPublisher
  #define MyAppPublisher "Momoko Ayase"
#endif
#ifndef DistDir
  #define DistDir "..\..\build\dist\windows\IntelliDo"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\build\dist\windows"
#endif
#ifndef SetupIcon
  #define SetupIcon "..\..\artwork\final\intellido.ico"
#endif
#ifndef NoticeFile
  #define NoticeFile "INSTALL-NOTICE.zh.txt"
#endif
#ifndef LicenseFilePath
  #define LicenseFilePath "..\..\LICENSE"
#endif

[Setup]
AppId=moe.momokko.intellido
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/Momoko-Ayase/IntelliDo
AppSupportURL=https://github.com/Momoko-Ayase/IntelliDo
AppCopyright=Copyright 2026 Momoko Ayase
DefaultDirName={localappdata}\Programs\IntelliDo
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
OutputDir={#OutputDir}
OutputBaseFilename=IntelliDo-{#MyAppVersion}-windows-x64
SetupIconFile={#SetupIcon}
UninstallDisplayIcon={app}\bin\intellido.ico
UninstallDisplayName={#MyAppName}
LicenseFile={#LicenseFilePath}
InfoBeforeFile={#NoticeFile}
AllowNoIcons=yes
CloseApplications=yes
MinVersion=10.0
UsePreviousAppDir=yes
DirExistsWarning=no
ChangesAssociations=no
DisableWelcomePage=no
SetupMutex=IntelliDoSetupMutex
WizardSizePercent=120
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoProductName={#MyAppName}
VersionInfoDescription={#MyAppName} Setup

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[InstallDelete]
Type: filesandordirs; Name: "{localappdata}\Momokko\IntelliDo\splash"
Type: filesandordirs; Name: "{localappdata}\JetBrains\IntelliDo\splash"

[Dirs]
Name: "{userappdata}\Momokko\IntelliDo\options"

[Files]
Source: "{#DistDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "default-config\early-access-registry.txt"; DestDir: "{userappdata}\Momokko\IntelliDo"; Flags: onlyifdoesntexist uninsneveruninstall
Source: "default-config\options\ide.general.xml"; DestDir: "{userappdata}\Momokko\IntelliDo\options"; Flags: onlyifdoesntexist uninsneveruninstall

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\bin\intellido64.exe"; IconFilename: "{app}\bin\intellido.ico"; WorkingDir: "{app}"
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\bin\intellido64.exe"; IconFilename: "{app}\bin\intellido.ico"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\bin\intellido64.exe"; Description: "Launch IntelliDo"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\jbr"
Type: filesandordirs; Name: "{app}\lib"
Type: filesandordirs; Name: "{app}\plugins"

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\intellido64.exe"; ValueType: string; ValueName: ""; ValueData: "{app}\bin\intellido64.exe"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\intellido64.exe"; ValueType: string; ValueName: "Path"; ValueData: "{app}\bin"

[Messages]
WelcomeLabel1=IntelliDo
WelcomeLabel2=This installs a per-user copy of IntelliDo, an unofficial LINUX DO desktop client.%n%nIt does not require administrator permission and does not register programming file types.
FinishedLabel=IntelliDo is installed. Start it from the Start menu, or choose Launch IntelliDo below.
