#!/bin/bash
# Post-edit hook: auto-compile when Kotlin files are modified
# Reads JSON from stdin to extract the edited file path

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | grep -oP '"file_path"\s*:\s*"\K[^"]+' | head -1)

if [[ "$FILE_PATH" == *.kt ]]; then
  cd /home/evengul/dev/mc-org/webapp && mvn compile -q 2>&1 | tail -10
fi
