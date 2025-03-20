import fs from "node:fs";

const basePath = "."

function toPath(subPath) {
  return `${basePath}/${subPath}`;
}

export default function() {
  return extract(toPath("tags/loot_table")).flatMap(subTable => subTable)
}

function extract(path) {
  const isDirectory = fs.lstatSync(path).isDirectory()
  if (isDirectory) {
    return fs.readdirSync(path).map(dir => {
      const newPath = `${path}/${dir}`;
      return extract(newPath);
    })
  }
  return extractFile(path);
}

function extractFile(path) {
  const content = JSON.parse(fs.readFileSync(path).toString());
  return {
    fromFile: path,
    type: content.type,
    requirements: [],
    ...extractContent(content)
  }
}

function extractContent(content) {
  if (!content.hasOwnProperty("pools")) {
    return {
      ignored: true
    }
  }
  try {
    return {
      result: content.pools.map(pool => pool.entries.map(entry => ({
        item: entry.name,
        count: 1
      })))
    }
  } catch (e) {
    return {
      error: true,
      message: e.message,
      data: content
    }
  }
}