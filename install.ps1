Write-Host "================================================"
Write-Host "        Distributed Computing Systems Lab"
Write-Host "        Lab File Downloader"
Write-Host "================================================"
Write-Host ""

# Base URL of your GitHub repo (raw content)
$repoBase = "https://raw.githubusercontent.com/ashbiry/dcs-suite/main"

# Progress bar makes Invoke-WebRequest very slow on Windows PowerShell 5.1
$ProgressPreference = 'SilentlyContinue'

# Practicals: id, repo folder, description, files (downloaded into ./practical-<id>/)
$practicals = @(
    @{ id = "1"; folder = "1"; desc = "Concurrent Echo Server (Sockets + Threads)";
       files = @("EchoServer.java", "EchoClient.java", "NOTES.md", "sample-output.txt") },

    @{ id = "2"; folder = "2"; desc = "Java RMI Calculator";
       files = @("Calculator.java", "CalculatorImpl.java", "RMIServer.java", "RMIClient.java", "NOTES.md", "sample-output.txt") },

    @{ id = "3"; folder = "3"; desc = "CORBA Calculator with Java IDL (JDK 8)";
       files = @("Calc.idl", "CalcServer.java", "CalcClient.java", "NOTES.md", "sample-output.txt") },

    @{ id = "4"; folder = "4"; desc = "Berkeley Clock Synchronization";
       files = @("Berkeley.java", "NOTES.md", "sample-output.txt") },

    @{ id = "5"; folder = "5"; desc = "Leader Election (Bully + Ring)";
       files = @("Election.java", "NOTES.md", "sample-output.txt") },

    @{ id = "6"; folder = "6"; desc = "MapReduce Word Count";
       files = @("WordCount.java", "NOTES.md", "sample-output.txt") },

    @{ id = "7"; folder = "7"; desc = "Termination Detection (Dijkstra-Scholten)";
       files = @("TerminationDetection.java", "NOTES.md", "sample-output.txt") },

    @{ id = "8"; folder = "8"; desc = "Name Server";
       files = @("NameServer.java", "NameClient.java", "NOTES.md", "sample-output.txt") },

    @{ id = "9"; folder = "9"; desc = "Pub/Sub with Web Service, ESB & JMS (Maven)";
       files = @("pom.xml", "src/main/java/Broker.java", "src/main/java/EsbService.java",
                 "src/main/java/Publisher.java", "src/main/java/Subscriber.java", "NOTES.md", "sample-output.txt") }
)

# Resource files (downloaded to current directory, not into a folder)
$resources = @(
    @{ id = "99"; name = "SETUP.md"; desc = "WSL / JDK / Maven Setup Guide" }
)

$script:failed = $false

function Get-File($url, $outPath, $label) {
    $dir = Split-Path $outPath -Parent
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    try {
        Invoke-WebRequest -Uri $url -OutFile $outPath -UseBasicParsing -ErrorAction Stop
        Write-Host "  ✅ $label"
    } catch {
        Write-Host "  ❌ Failed: $label"
        $script:failed = $true
    }
}

function Get-Practical($p) {
    $outDir = "practical-$($p.id)"
    Write-Host "`nDownloading $outDir ($($p.desc))..."
    foreach ($file in $p.files) {
        Get-File "$repoBase/$($p.folder)/$file" (Join-Path $outDir $file) $file
    }
}

function Get-Resource($r) {
    Write-Host "`nDownloading $($r.name)..."
    Get-File "$repoBase/$($r.name)" $r.name $r.name
}

function Complete-Run($message, $next) {
    if ($script:failed) {
        Write-Host "`n⚠️  Some files failed to download. Check your internet / the repository link."
    } else {
        Write-Host "`n✅ $message"
        if ($next) { Write-Host "`n$next" }
    }
    Write-Host "`nDone!"
}

# Display menu
Write-Host "Available Practicals:"
Write-Host "---------------------"
foreach ($p in $practicals) {
    Write-Host "  $($p.id). $($p.desc)"
    Write-Host "     [practical-$($p.id)] $($p.files -join ', ')"
}

Write-Host ""
Write-Host "Resource Files:"
Write-Host "---------------"
foreach ($r in $resources) {
    Write-Host "  $($r.id). $($r.desc)  [$($r.name)]"
}

Write-Host ""
Write-Host "   0. Download everything (all practicals + resources)"

# Prompt for choice
$allIds = @($practicals | ForEach-Object { $_.id }) + @($resources | ForEach-Object { $_.id }) + @("0")
$validIds = $allIds -join ", "
Write-Host ""
$choice = "$(Read-Host "Enter your choice [$validIds]")".Trim()

if ($choice -eq "0") {
    foreach ($p in $practicals) { Get-Practical $p }
    foreach ($r in $resources)  { Get-Resource $r }
    Complete-Run "Everything downloaded to $((Get-Location).Path)\" $null
    return
}

$selectedResource = $resources | Where-Object { $_.id -eq $choice }
if ($selectedResource) {
    Get-Resource $selectedResource
    Complete-Run "File saved as: $($selectedResource.name)" $null
    return
}

$selected = $practicals | Where-Object { $_.id -eq $choice }
if (-not $selected) {
    Write-Host "Invalid choice! Please enter one of: $validIds"
    return
}

Get-Practical $selected
Complete-Run "All files downloaded to ./practical-$($selected.id)/" "Next: cd practical-$($selected.id); notepad NOTES.md"
