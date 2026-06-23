#!/usr/bin/env node

import {createWriteStream, existsSync, mkdirSync} from 'node:fs';
import {unlink} from 'node:fs/promises';
import https from 'node:https';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const rootDir = path.resolve(process.cwd());
const monitorDir = path.join(rootDir, '.monitor');
const toolsDir = path.join(monitorDir, 'tools');
const outputFile = path.join(monitorDir, 'detekt.sarif');
const detektVersion = '1.23.8';
const detektJar = path.join(toolsDir, `detekt-cli-${detektVersion}-all.jar`);
const detektUrl = `https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/${detektVersion}/detekt-cli-${detektVersion}-all.jar`;
const detektConfig = path.join(rootDir, 'detekt', 'detekt.yml');

const sourceDirs = [
  path.join(rootDir, 'android', 'app', 'src', 'main', 'java'),
  path.join(rootDir, 'android', 'app', 'src', 'test', 'kotlin'),
].filter(dirPath => existsSync(dirPath));

if (sourceDirs.length === 0) {
  console.error('[detekt-scan] No Kotlin source directories were found for scanning.');
  process.exit(2);
}

mkdirSync(monitorDir, {recursive: true});
mkdirSync(toolsDir, {recursive: true});

const downloadFile = (url, targetPath) =>
  new Promise((resolve, reject) => {
    const fileStream = createWriteStream(targetPath);
    const request = https.get(url, response => {
      if (response.statusCode !== 200) {
        fileStream.close();
        reject(new Error(`Download failed with status ${response.statusCode}: ${url}`));
        return;
      }

      response.pipe(fileStream);
      fileStream.on('finish', () => {
        fileStream.close();
        resolve();
      });
    });

    request.on('error', error => {
      fileStream.close();
      reject(error);
    });
  });

if (!existsSync(detektJar)) {
  console.log(`[detekt-scan] Downloading detekt CLI ${detektVersion}...`);
  try {
    await downloadFile(detektUrl, detektJar);
  } catch (error) {
    try {
      await unlink(detektJar);
    } catch {
      // Ignore cleanup errors for partial downloads.
    }
    console.error('[detekt-scan] Unable to download detekt CLI:', error.message);
    process.exit(2);
  }
}

const detektArgs = [
  '-jar',
  detektJar,
  '--build-upon-default-config',
  '--input',
  sourceDirs.join(','),
  '--report',
  `sarif:${outputFile}`,
  '--parallel',
];

if (existsSync(detektConfig)) {
  detektArgs.push('--config', detektConfig);
}

const result = spawnSync('java', detektArgs, {
  cwd: rootDir,
  stdio: 'inherit',
  shell: false,
});

const exitCode = result.status ?? 1;

if (!existsSync(outputFile)) {
  console.error('[detekt-scan] Detekt completed without SARIF output:', outputFile);
  process.exit(exitCode === 0 ? 2 : exitCode);
}

console.log(`[detekt-scan] Report written to ${path.relative(rootDir, outputFile).replace(/\\/g, '/')}`);

if (exitCode !== 0) {
  process.exit(exitCode);
}
