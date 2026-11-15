#!/usr/bin/env bash

echo "================================================"
echo "        Distributed Computing Systems Lab"
echo "        Lab File Downloader"
echo "================================================"
echo ""

# Base URL of your GitHub repo (raw content)
# DCS_REPO_BASE can override it (used only for local testing)
REPO_BASE="${DCS_REPO_BASE:-https://raw.githubusercontent.com/ashbiry/dcs-suite/main}"

# Practicals definition
# Format: ID|FOLDER|DESCRIPTION|FILE1,FILE2,...
# Files are downloaded from <REPO_BASE>/<FOLDER>/<FILE> into ./practical-<ID>/<FILE>
PRACTICALS=(
    "1|1|Concurrent Echo Server (Sockets + Threads)|EchoServer.java,EchoClient.java,NOTES.md,sample-output.txt"
    "2|2|Java RMI Calculator|Calculator.java,CalculatorImpl.java,RMIServer.java,RMIClient.java,NOTES.md,sample-output.txt"
    "3|3|CORBA Calculator with Java IDL (JDK 8)|Calc.idl,CalcServer.java,CalcClient.java,NOTES.md,sample-output.txt"
    "4|4|Berkeley Clock Synchronization|Berkeley.java,NOTES.md,sample-output.txt"
    "5|5|Leader Election (Bully + Ring)|Election.java,NOTES.md,sample-output.txt"
    "6|6|MapReduce Word Count|WordCount.java,NOTES.md,sample-output.txt"
    "7|7|Termination Detection (Dijkstra-Scholten)|TerminationDetection.java,NOTES.md,sample-output.txt"
    "8|8|Name Server|NameServer.java,NameClient.java,NOTES.md,sample-output.txt"
    "9|9|Pub/Sub with Web Service, ESB & JMS (Maven)|pom.xml,src/main/java/Broker.java,src/main/java/EsbService.java,src/main/java/Publisher.java,src/main/java/Subscriber.java,NOTES.md,sample-output.txt"
)

# Resource files (downloaded to current directory)
RESOURCES=(
    "99|SETUP.md|WSL / JDK / Maven Setup Guide"
)

FAILED=0

# Download one practical entry into ./practical-<id>/
download_practical() {
    local id folder desc files out_dir file file_url out_path
    IFS='|' read -r id folder desc files <<< "$1"
    out_dir="practical-$id"
    mkdir -p "$out_dir"

    echo ""
    echo "Downloading $out_dir ($desc)..."

    IFS=',' read -ra file_array <<< "$files"
    for file in "${file_array[@]}"; do
        file_url="$REPO_BASE/$folder/$file"
        out_path="$out_dir/$file"
        mkdir -p "$(dirname "$out_path")"
        if curl -fsSL "$file_url" -o "$out_path"; then
            echo "  ✅ $file"
        else
            echo "  ❌ Failed: $file"
            FAILED=1
        fi
    done
}

# Download one resource file into the current directory
download_resource() {
    local id name desc
    IFS='|' read -r id name desc <<< "$1"
    echo ""
    echo "Downloading $name..."
    if curl -fsSL "$REPO_BASE/$name" -o "$name"; then
        echo "  ✅ $name"
    else
        echo "  ❌ Failed: $name"
        FAILED=1
    fi
}

finish() {
    echo ""
    if [ "$FAILED" -eq 1 ]; then
        echo "⚠️  Some files failed to download. Check your internet / the repository link."
    else
        echo "✅ $1"
        if [ -n "${2:-}" ]; then
            echo ""
            echo "$2"
        fi
    fi
    echo ""
    echo "Done!"
    exit "$FAILED"
}

# Display menu
echo "Available Practicals:"
echo "---------------------"
for entry in "${PRACTICALS[@]}"; do
    IFS='|' read -r id folder desc files <<< "$entry"
    echo "  $id. $desc"
    echo "     [practical-$id] ${files//,/, }"
done

echo ""
echo "Resource Files:"
echo "---------------"
for entry in "${RESOURCES[@]}"; do
    IFS='|' read -r id name desc <<< "$entry"
    echo "  $id. $desc  [$name]"
done

echo ""
echo "   0. Download everything (all practicals + resources)"

# Build valid IDs string
VALID_IDS=""
for entry in "${PRACTICALS[@]}" "${RESOURCES[@]}"; do
    IFS='|' read -r id _ <<< "$entry"
    VALID_IDS="${VALID_IDS:+$VALID_IDS, }$id"
done
VALID_IDS="$VALID_IDS, 0"

echo ""
# Read from the terminal so this also works with: curl ... | bash
read -rp "Enter your choice [$VALID_IDS]: " CHOICE < /dev/tty
CHOICE="${CHOICE//[[:space:]]/}"

# Everything
if [[ "$CHOICE" == "0" ]]; then
    for entry in "${PRACTICALS[@]}"; do
        download_practical "$entry"
    done
    for entry in "${RESOURCES[@]}"; do
        download_resource "$entry"
    done
    finish "Everything downloaded to $(pwd)/"
fi

# Resource file
for entry in "${RESOURCES[@]}"; do
    IFS='|' read -r id name _ <<< "$entry"
    if [[ "$id" == "$CHOICE" ]]; then
        download_resource "$entry"
        finish "File saved as: $name"
    fi
done

# Practical
for entry in "${PRACTICALS[@]}"; do
    IFS='|' read -r id _ <<< "$entry"
    if [[ "$id" == "$CHOICE" ]]; then
        download_practical "$entry"
        finish "All files downloaded to ./practical-$id/" "Next: cd practical-$id && cat NOTES.md"
    fi
done

echo "Invalid choice! Please enter one of: $VALID_IDS"
exit 1
