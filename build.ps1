param(
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$AndroidSdk,
    [Parameter(Mandatory)][string]$BuildToolsVersion,
    [Parameter(Mandatory)][string]$XposedApiJar
)

$ErrorActionPreference = 'Stop'
$base = $PSScriptRoot
$build = Join-Path $base 'build'
$javaBin = Join-Path $JavaHome 'bin'
$androidJar = Join-Path $AndroidSdk 'platforms/android-35/android.jar'
$buildTools = Join-Path $AndroidSdk "build-tools/$BuildToolsVersion"
$apiJar = (Resolve-Path -LiteralPath $XposedApiJar).Path
New-Item -ItemType Directory -Path $build,(Join-Path $build 'classes'),(Join-Path $build 'dex'),(Join-Path $build 'checks') -Force | Out-Null
function Run-Native($exe, $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed: $exe ($LASTEXITCODE)" }
}
$sources = @(Get-ChildItem -LiteralPath (Join-Path $base 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
Run-Native (Join-Path $javaBin 'javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-cp',"$androidJar;$apiJar",'-d',(Join-Path $build 'classes')) + $sources)
Run-Native (Join-Path $javaBin 'jar.exe') @('cf',(Join-Path $build 'compiled.jar'),'-C',(Join-Path $build 'classes'),'.')
Run-Native (Join-Path $javaBin 'java.exe') @('-cp',(Join-Path $buildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','28','--lib',$androidJar,'--classpath',$apiJar,'--output',(Join-Path $build 'dex'),(Join-Path $build 'compiled.jar'))
Run-Native (Join-Path $buildTools 'aapt2.exe') @('link','-o',(Join-Path $build 'unsigned.apk'),'--manifest',(Join-Path $base 'AndroidManifest.xml'),'-I',$androidJar,'-A',(Join-Path $base 'assets'))
Run-Native (Join-Path $javaBin 'jar.exe') @('uf',(Join-Path $build 'unsigned.apk'),'-C',(Join-Path $build 'dex'),'classes.dex')
Run-Native (Join-Path $buildTools 'zipalign.exe') @('-f','4',(Join-Path $build 'unsigned.apk'),(Join-Path $build 'aligned.apk'))
$key = Join-Path $build 'local-signing.p12'
if (!(Test-Path -LiteralPath $key)) {
    Run-Native (Join-Path $javaBin 'keytool.exe') @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-storepass','local-build-only','-keypass','local-build-only','-alias','local','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=Local XXJZ Accessibility Restore','-noprompt')
}
$out = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$apk = Join-Path $out 'XxjzAccessibilityRestore-1.0.1.apk'
Run-Native (Join-Path $javaBin 'java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'sign','--ks',$key,'--ks-key-alias','local','--ks-pass','pass:local-build-only','--out',$apk,(Join-Path $build 'aligned.apk'))
Run-Native (Join-Path $javaBin 'java.exe') @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'verify','--verbose',$apk)




