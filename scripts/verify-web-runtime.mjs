import { readFile } from "node:fs/promises";

const readPackage = async (path) => JSON.parse(await readFile(path, "utf8"));
const root = await readPackage(new URL("../package.json", import.meta.url));
const web = await readPackage(new URL("../web/package.json", import.meta.url));

for (const dependency of ["next", "react", "react-dom"]) {
  const rootVersion = root.dependencies?.[dependency];
  const webVersion = web.dependencies?.[dependency];
  if (!rootVersion || rootVersion !== webVersion) {
    throw new Error(`${dependency} must use one identical version at the monorepo root and in web`);
  }
}

console.log("Next.js and React runtime versions are aligned.");
