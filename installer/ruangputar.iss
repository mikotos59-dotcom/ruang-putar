; =====================================================================
;  Installer Ruang Putar (Inno Setup 6) — versi mesin NewPipe (Kotlin)
;  Dikompilasi oleh GitHub Actions atau buat_installer.bat:
;     ISCC /DVersiApp=0.8.0 /DSumber=..\build\compose\binaries\main\app\RuangPutar installer\ruangputar.iss
;  Hasil: dist\RuangPutar-<versi>-setup.exe
; =====================================================================

#ifndef VersiApp
  #define VersiApp "0.0.0"
#endif
#ifndef Sumber
  #define Sumber "..\build\compose\binaries\main\app\RuangPutar"
#endif

[Setup]
; AppId SAMA dengan Ruang Putar lama (dan Lantun): versi ini menggantikannya tanpa uninstall
AppId={{6F2A9C41-3B7E-4E55-9D0B-1A7C2E8F4B63}
AppName=Ruang Putar
AppVersion={#VersiApp}
AppVerName=Ruang Putar {#VersiApp}
AppPublisher=@SuS_X_x
AppCopyright=Dibuat oleh @SuS_X_x
VersionInfoVersion={#VersiApp}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\RuangPutar
UsePreviousAppDir=yes
DisableProgramGroupPage=yes
DisableDirPage=auto
OutputDir=..\dist
OutputBaseFilename=RuangPutar-{#VersiApp}-setup
SetupIconFile=ruangputar.ico
UninstallDisplayIcon={app}\RuangPutar.exe
UninstallDisplayName=Ruang Putar
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=force
RestartApplications=no
ChangesAssociations=yes

[Messages]
SetupWindowTitle=Pemasangan %1
WelcomeLabel1=Selamat datang di pemasangan [name]
WelcomeLabel2=Wizard ini akan memasang [name/ver] — pemutar musik ringan — di komputer kamu.%n%nSemua yang dibutuhkan sudah ada di dalamnya: tidak perlu memasang Java atau aplikasi lain.%n%nKalau Ruang Putar versi lama terpasang, ia akan digantikan. Favorit, riwayat, rak, dan penyanyi yang diikuti ikut dipindahkan.
WizardSelectDir=Pilih Lokasi Pemasangan
SelectDirDesc=Di mana [name] akan dipasang?
SelectDirLabel3=[name] akan dipasang di folder berikut.
SelectDirBrowseLabel=Klik Berikutnya untuk melanjutkan, atau Telusuri untuk memilih folder lain.
WizardSelectTasks=Pilihan Tambahan
SelectTasksDesc=Ada tambahan yang ingin dipasang?
SelectTasksLabel2=Pilih tambahan yang diinginkan, lalu klik Berikutnya.
WizardReady=Siap Dipasang
ReadyLabel1=[name] siap dipasang di komputer kamu.
ReadyLabel2a=Klik Pasang untuk mulai, atau Kembali untuk mengubah pilihan.
WizardInstalling=Sedang Memasang
InstallingLabel=Tunggu sebentar, [name] sedang dipasang...
FinishedHeadingLabel=Pemasangan [name] selesai
FinishedLabel=[name] sudah terpasang dan bisa dibuka dari Start Menu.
FinishedLabelNoIcons=[name] sudah terpasang.
ClickFinish=Klik Selesai untuk menutup wizard.
ButtonBack=< &Kembali
ButtonNext=&Berikutnya >
ButtonInstall=&Pasang
ButtonFinish=&Selesai
ButtonCancel=Batal
ButtonBrowse=&Telusuri...
ButtonYes=&Ya
ButtonNo=&Tidak
ExitSetupTitle=Keluar dari Pemasangan
ExitSetupMessage=Pemasangan belum selesai. Kalau keluar sekarang, [name] tidak akan terpasang.%n%nKeluar dari pemasangan?
ConfirmUninstall=Yakin ingin menghapus %1 dari komputer ini?
UninstalledAll=%1 sudah dihapus dari komputer ini.

[CustomMessages]
AdditionalIcons=Shortcut:
CreateDesktopIcon=Buat shortcut di &Desktop
Asosiasi=Tambahkan "Buka dengan Ruang Putar" untuk file musik (MP3, M4A, FLAC, OGG, OPUS, WAV)
GrupFile=File:

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "asosiasi"; Description: "{cm:Asosiasi}"; GroupDescription: "{cm:GrupFile}"; Flags: unchecked

[InstallDelete]
; Ruang Putar lama (Python + yt-dlp + ffmpeg)
Type: filesandordirs; Name: "{app}\_internal"
; isi versi sebelumnya dibersihkan dulu supaya tidak ada file .jar lama yang tertinggal
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
; Lantun (nama paling lama)
Type: filesandordirs; Name: "{autopf}\Lantun"
Type: files; Name: "{autoprograms}\Lantun.lnk"
Type: files; Name: "{autodesktop}\Lantun.lnk"

[Files]
Source: "{#Sumber}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Ruang Putar"; Filename: "{app}\RuangPutar.exe"
Name: "{autodesktop}\Ruang Putar"; Filename: "{app}\RuangPutar.exe"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\RuangPutar.Media"; ValueType: string; ValueData: "File musik Ruang Putar"; Flags: uninsdeletekey; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\RuangPutar.Media\DefaultIcon"; ValueType: string; ValueData: "{app}\RuangPutar.exe,0"; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\RuangPutar.Media\shell\open\command"; ValueType: string; ValueData: """{app}\RuangPutar.exe"" ""%1"""; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\Applications\RuangPutar.exe\shell\open\command"; ValueType: string; ValueData: """{app}\RuangPutar.exe"" ""%1"""; Flags: uninsdeletekey; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.mp3\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.m4a\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.flac\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.ogg\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.opus\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi
Root: HKA; Subkey: "Software\Classes\.wav\OpenWithProgids"; ValueType: string; ValueName: "RuangPutar.Media"; ValueData: ""; Flags: uninsdeletevalue; Tasks: asosiasi

[Run]
; Tanpa "skipifsilent": saat pembaruan otomatis (mode diam), Ruang Putar langsung dibuka lagi
Filename: "{app}\RuangPutar.exe"; Description: "Buka Ruang Putar sekarang"; Flags: nowait postinstall

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if SuppressibleMsgBox('Hapus juga data Ruang Putar?' + #13#10 + #13#10 +
       'Termasuk favorit, riwayat, pengaturan, rak Beranda, dan cache lagu. ' +
       'Lagu yang sudah kamu unduh di folder Music tidak ikut terhapus.',
       mbConfirmation, MB_YESNO or MB_DEFBUTTON2, IDNO) = IDYES then
    begin
      DelTree(ExpandConstant('{userappdata}\RuangPutar'), True, True, True);
      DelTree(ExpandConstant('{localappdata}\RuangPutar'), True, True, True);
      DelTree(ExpandConstant('{userappdata}\Lantun'), True, True, True);
    end;
  end;
end;
