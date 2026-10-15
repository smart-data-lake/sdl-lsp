const fs = require('fs');
const os = require('os');
const path = require('path');
const { ProgressLocation, window, workspace } = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

const RELEASES_URL = 'https://github.com/smart-data-lake/sdl-lsp/releases';
// Version of the extension when not built by the release workflow
const DEV_VERSION = '0.0.1';

let client;

exports.activate = async (context) => {
  const config = workspace.getConfiguration('sdlLsp');
  const jar = resolveJarPath(config.get('jarPath') || '.sdlb/sdl-lsp.jar');
  if (!jar) {
    window.showErrorMessage('SDLB Language Server: open the folder of your SDLB project, or set "sdlLsp.jarPath" to an absolute path.');
    return;
  }
  if (!fs.existsSync(jar)) {
    try {
      await downloadJar(jarUrl(context.extension.packageJSON.version), jar);
    } catch (e) {
      window.showErrorMessage(`SDLB Language Server: could not download sdl-lsp.jar to ${jar}: ${e.message}`);
      return;
    }
  }
  // A command without transport uses stdio, as expected by the server
  const serverOptions = {
    command: config.get('javaPath') || 'java',
    args: ['-jar', jar, '--client', 'vscode'],
    options: { env: { ...process.env, ...config.get('env') } },
  };
  client = new LanguageClient('sdlLsp', 'SDLB Language Server', serverOptions, {
    documentSelector: [{ scheme: 'file', language: 'hocon' }],
  });
  return client.start();
};

exports.deactivate = () => client?.stop();

// Relative paths are relative to the (first) workspace folder, i.e. the project root
function resolveJarPath(jarPath) {
  if (jarPath === '~' || jarPath.startsWith('~/')) return path.join(os.homedir(), jarPath.slice(1));
  if (path.isAbsolute(jarPath)) return jarPath;
  const folder = workspace.workspaceFolders?.[0];
  return folder && path.join(folder.uri.fsPath, jarPath);
}

// The jar of the same release as the extension, the latest one for a development build
function jarUrl(version) {
  return version === DEV_VERSION
    ? `${RELEASES_URL}/latest/download/sdl-lsp.jar`
    : `${RELEASES_URL}/download/v${version}/sdl-lsp-${version}.jar`;
}

function downloadJar(url, jar) {
  return window.withProgress(
    { location: ProgressLocation.Notification, title: `SDLB Language Server: downloading ${url}` },
    async () => {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
      fs.mkdirSync(path.dirname(jar), { recursive: true });
      // Written next to the jar and renamed, so that an interrupted download leaves no broken jar
      const partial = `${jar}.part`;
      fs.writeFileSync(partial, Buffer.from(await response.arrayBuffer()));
      fs.renameSync(partial, jar);
    },
  );
}
