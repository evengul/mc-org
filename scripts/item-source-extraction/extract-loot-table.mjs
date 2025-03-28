import fs from "node:fs";

const basePath = "."

function toPath(subPath) {
  return `${basePath}/${subPath}`;
}

export default function () {
  const lootTable = []
  const path = toPath("tags/loot_table");
  extract(path, lootTable)
  return lootTable;
}

function extract(path, lootTable) {
  const isDirectory = fs.lstatSync(path).isDirectory()
  if (isDirectory) {
    return fs.readdirSync(path).map(dir => {
      const newPath = `${path}/${dir}`;
      return extract(newPath, lootTable);
    })
  }
  return extractFile(path, lootTable);
}

function extractFile(path, lootTable) {
  const content = JSON.parse(fs.readFileSync(path).toString());
  const allResults = []
  extractContent(path, content, allResults)
  lootTable.push({
    fromFile: path, type: content.type, requirements: [], result: [...allResults]
  });
}

const ignoredValues = ["minecraft:entities/sheep", "minecraft:shearing/sheep", "minecraft:shearing/mooshroom", "minecraft:gameplay/fishing/fish", "minecraft:empty"]

function extractContent(path, content, result = []) {
  if (!content.hasOwnProperty("pools")) {
    return
  }
  try {
    for (let i = 0; i < content.pools.length; i++) {
      for (let j = 0; j < content.pools[i].entries.length; j++) {
        const entry = content.pools[i].entries[j];
        if (entry.type === "minecraft:alternatives") {
          if (!entry) {
            console.error("No entry", path, 0, entry)
            return
          }
          if (!entry.children) {
            console.error("No children", path, 0, entry)
            return
          }
          extractAlternatives(path, entry, 0, result)
          return
        }
        if (entry.type === "minecraft:loot_table") {
          extractContent(path, entry.value, result)
          return
        }
        if (ignoredValues.some(ignoredValue => entry.type.includes(ignoredValue) || (entry.hasOwnProperty("value") && entry.value.includes(ignoredValue)))) {
          return
        }
        if (!entry.name) {
          console.error("No name", path, 0, entry)
          return
        }
        if (!result.some(item => item.item === entry.name)) {
          result.push({
            item: entry.name,
            count: 1
          })
        }
      }
    }
  } catch (e) {
    console.error("Error", path, e.message)
  }
}

function extractAlternatives(path, entry, level, lootTable) {
  if (!entry) {
    console.error("No entry", path, level, entry)
    return
  }
  if (!entry.children) {
    console.error("No children", path, level, entry)
    return
  }
  entry.children.forEach((child) => {
    if (child.hasOwnProperty("value") && ignoredValues.some(ignoredValue => child.value.includes(ignoredValue))) {
      return
    }
    if (child.type === "minecraft:alternatives") {
      extractAlternatives(path, child, level + 1, lootTable)
      return
    }
    if (!child.name) {
      console.error("No name", path, level, child)
      return
    }
    if (!lootTable.some(loot => loot.name === child.name)) {
      lootTable.push({
        item: child.name, count: 1
      })
    }
  })
}