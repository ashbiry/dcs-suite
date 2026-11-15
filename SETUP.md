# DCS Practical Exam – Environment Setup (WSL + Java)

Goal: be able to rebuild a working Java environment on an unknown lab PC in ~15 minutes.
Exam: 16 Nov 2026, 8:00–11:00 am, room 606 (Roll 09).

## 0. Order of preference on exam day
1. Check what is already on the PC first: open a terminal (cmd / PowerShell) and run
   `java -version` and `javac -version`. Plain Windows JDK + Notepad/any IDE is enough for
   Practicals 1, 2, 4, 5, 6, 7, 8. **Don't install WSL if you don't need it.**
2. Only Practical 3 (CORBA) needs a special JDK (8). Practical 9 needs Maven + internet.
3. If you need a fuller setup, use WSL (below). Ask the invigilator before installing anything.

## 1. Install WSL (Windows 10 2004+/Windows 11)
Open PowerShell **as Administrator**:
```
wsl --install -d Ubuntu
```
Reboot if asked, open "Ubuntu" from the Start menu, create a username/password.
If `wsl` is already installed: `wsl -l -v` to list distros.
Could fail in the lab (no admin rights / virtualisation off). Then fall back to Windows-native
JDK (see section 5).

## 2. Install Java + Maven inside Ubuntu
```
sudo apt update
sudo apt install -y openjdk-8-jdk openjdk-17-jdk maven
```
Check both exist: `ls /usr/lib/jvm`

### Switching Java version (important!)
Practical 3 (CORBA) needs JDK 8. Everything else works on 8 or 17. Easiest rule:
**just use JDK 8 for all practicals.** Switch with:
```
sudo update-alternatives --config java
sudo update-alternatives --config javac
java -version       # confirm
```
Or per terminal:
```
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```
(JDK 11+ removed `idlj`, `orbd` and the `org.omg.*` CORBA classes. That is why CORBA is the trap.)

## 3. Where to keep files
Work inside the Linux filesystem, e.g. `~/dcs/<n>/` (faster, fewer permission issues).
Windows files are visible at `/mnt/c/Users/<you>/...`.
From Windows Explorer you can open WSL files at `\\wsl$\Ubuntu\home\<user>`.
Editors: `nano file.java`, or `code .` if VS Code is installed on Windows (it connects to WSL).

## 4. Basic compile/run loop (used by almost every practical)
```
javac *.java          # compile everything in the folder
java ServerClass      # run in terminal 1
java ClientClass      # run in terminal 2 (open a second Ubuntu window)
```
Multiple terminals: open several Ubuntu windows, or use `tmux`.
Servers/clients talk over `localhost`, so no network setup is needed.

## 5. If WSL is not possible: Windows-native
- Check `java -version`. If JDK ≥ 11, CORBA needs a JDK 8 download
  (Adoptium Temurin 8: https://adoptium.net) – unzip anywhere, then in PowerShell:
  ```
  $env:JAVA_HOME = "C:\path\to\jdk8"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  ```
  (Set this in each new terminal – exactly what the class rep did for Practical 3.)

## 6. Per-practical environment needs (summary)
| # | Needs | Notes |
|---|---|---|
| 1 | any JDK | plain `javac` |
| 2 | any JDK ≤ 17 | use modern RMI: no `rmic`, stubs are generated at runtime |
| 3 | **JDK 8** | `idlj`, `orbd`/`tnameserv` ship with JDK 8 |
| 4–8 | any JDK | plain Java |
| 9 | Maven + internet | ActiveMQ is downloaded as a dependency; modern JDK fine |

## 7. Common errors
- `Address already in use` → a previous server is still running; `Ctrl+C` it or change the port.
- `Connection refused` → start the server first.
- `javac: command not found` → only a JRE is installed; install the JDK package.
- `package org.omg.CORBA does not exist` → you're on JDK 11+; switch to JDK 8.
- Maven stuck downloading → check internet / proxy; run once with `mvn -o` only if already cached.
- WSL `sudo apt` fails → run `sudo apt update` first; check internet in WSL with `ping 8.8.8.8`.
