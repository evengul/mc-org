#!/usr/bin/env bash

NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

if [ -d 'extracted/version_1.21.4' ]; then
  rm -rf extracted/version_1.21.4
fi

mkdir extracted/version_1.21.4
cd extracted/version_1.21.4 || exit 1

echo 'Extracting content'
jar -xf ../../servers/1.21.4.jar META-INF/versions/1.21.4/server-1.21.4.jar
jar -xf ../../servers/1.21.4.jar version.json
echo 'Done extracting'

echo 'Moving relevant files to outer folder'
mv META-INF/versions/1.21.4/server-1.21.4.jar server-1.21.4.jar

echo 'Removing now unused META-INF folder'
rm -rf META-INF

echo 'Extracting assets'
jar -xf server-1.21.4.jar assets/minecraft/lang/en_us.json
echo 'Extracting loot tables'
jar -xf server-1.21.4.jar data/minecraft/loot_table
echo 'Extracting recipes'
jar -xf server-1.21.4.jar data/minecraft/recipe
#echo 'Extracting world generation'
#jar -xf server-1.21.4.jar data/minecraft/worldgen
#echo 'Extracting tags/block'
#jar -xf server-1.21.4.jar data/minecraft/tags/block
#echo 'Extracting tags/item'
#jar -xf server-1.21.4.jar data/minecraft/tags/item
#echo 'Extracting tags/worldgen'
#jar -xf server-1.21.4.jar data/minecraft/tags/worldgen

echo 'Move relevant files to outer folder'
mv assets/minecraft/lang/en_us.json names.json

mkdir tags
mv data/minecraft/* tags

echo 'Remove now empty folders'
rm -rf assets
rm -rf data

echo 'Remove extracted server jar'
rm server-1.21.4.jar

mkdir finalized

echo 'Running node script to extract required data'
nvm exec 22.14.0 node --experimental-modules ../../extract-item-sources.mjs

echo 'Remove now unused files'
rm -rf tags
rm version.json

mv finalized/data.json data.json
rm -rf finalized

echo 'Add to git'
git add .

echo 'Done'