#!/bin/bash

set -e

NAME="Mutserve"
VERSION="v2.0.0-rc"
GITHUB_USER="seppinho"
GITHUB_REPO="mutserve"
EXECUTABLE="mutserve"
ZIP="mutserve.zip"

INSTALLER_URL=https://github.com/${GITHUB_USER}/${GITHUB_REPO}/releases/download/${VERSION}/${ZIP}


echo "Installing ${NAME} ${VERSION}..."

echo "Downloading ${NAME} from ${INSTALLER_URL}..."
curl -fL ${INSTALLER_URL} -o ${ZIP}

# execute installer
unzip ./${ZIP}

# change mod for executables
chmod +x ./${EXECUTABLE}

# remove installer
rm ./${ZIP}

echo ""
GREEN='\033[0;32m'
NC='\033[0m'
echo -e "${GREEN}${NAME} ${VERSION} installation completed. Have fun!${NC}"
echo ""

