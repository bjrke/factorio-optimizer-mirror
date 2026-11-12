// The /squash command: squash, rewrite the commit message, and stop.
//
// A plugin command's `execute` handler runs in the server and is never
// submitted to a model, so nothing runs after the squash. The handler runs
// squash-start.sh, asks a child session with no conversation history for a
// commit message with the transient `session.generate` call, and commits
// through squash-finish.sh. The parent conversation is not part of the child's
// context. The result is posted as a user message with `resume: false`; the
// message is visible, but the session never wakes, so there is no agent turn.
//
// The scripts resolve against the plugin's location; every git call runs in the
// session's directory, so a session moved to a worktree squashes its own
// checkout.
//
// A local plugin edit may need `opencode service restart`.

import { spawn } from "node:child_process";
import { join } from "node:path";

const SCRIPTS = ".opencode/scripts";
const MAX_DIFF = 200_000;

const MESSAGE_PROMPT = `Write the commit message for a squash merge whose net change is the staged change below.

Rules:
- Use only the change below: you cannot read the repository or any conversation.
- Headline at most 50 characters, imperative mood, American English, no trailing period, no !<MR> suffix.
- One blank line, then a body wrapped at 72 characters that explains why the change was made.
- Keep the body to the essentials: no novel-length recap, no file-by-file recap, no repetition of documentation or knowledge notes, only summarize.
- Do not claim tests or checks the diff does not show.
- Do not mention the squash.
- Describe the net change as a whole, not the individual commits.
- If the patch is truncated, use the complete file list to cover the whole change.
- Output only the message.

Staged change (whitespace ignored):
`;

function run(command, args, options = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { cwd: options.cwd });
    const maxBuffer = options.maxBuffer ?? 1024 * 1024;
    let stdout = "";
    let stderr = "";
    let error;
    let settled = false;

    const settle = (result) => {
      if (settled) return;
      settled = true;
      resolve(result);
    };

    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
      if (stdout.length > maxBuffer) {
        error = new Error(`stdout exceeded ${maxBuffer} characters`);
        child.kill();
      }
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", (err) => {
      error = err;
      settle({ stdout, stderr, status: null, error });
    });
    child.on("close", (status) => settle({ stdout, stderr, status, error }));

    child.stdin.on("error", () => {});
    child.stdin.end(options.input ?? "");
  });
}

function outputOf(result) {
  return `${result.stdout ?? ""}${result.stderr ?? ""}`.trim();
}

function failure(result) {
  if (result.error) return `SQUASH: FAIL (${result.error.message})`;
  const output = outputOf(result);
  if (output) return output;
  return result.status === 0 ? "SQUASH: FAIL (no output)" : `SQUASH: FAIL (exited ${result.status})`;
}

// The scripts ship with the plugin, so resolve them against the plugin's own
// location. The git work runs in the session's directory instead, which may be
// a worktree elsewhere.
function scriptPath(ctx, name) {
  return join(ctx.location.directory, SCRIPTS, name);
}

async function finish(ctx, directory, former, message, reason) {
  const finishScript = scriptPath(ctx, "squash-finish.sh");
  const args = reason ? [finishScript, former, reason] : [finishScript, former];
  return failure(await run("bash", args, { cwd: directory, input: message }));
}

async function stagedDiff(directory) {
  const result = await run("git", ["diff", "--cached", "-w", "origin/main"], {
    cwd: directory,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error) return { error: result.error.message };
  if (result.status !== 0) return { error: outputOf(result) || `git diff exited ${result.status}` };
  const diff = (result.stdout ?? "").trim();
  return diff ? { diff } : { error: "no staged diff" };
}

// The complete file list stays small even when the patch is truncated, so the
// message always sees the full scope of the change.
async function stagedStat(directory) {
  const result = await run("git", ["diff", "--cached", "-w", "--stat", "origin/main"], {
    cwd: directory,
    maxBuffer: 4 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) return "";
  return (result.stdout ?? "").trim();
}

function commitMessage(generated) {
  return (generated?.text ?? "").trim();
}

function withDiff(diff, stat) {
  const patch =
    diff.length <= MAX_DIFF
      ? diff
      : `${diff.slice(0, MAX_DIFF)}\n[patch truncated after ${MAX_DIFF} characters; the file list above is complete]`;
  const files = stat ? `Changed files:\n${stat}\n\nPatch:\n` : "";
  return `${MESSAGE_PROMPT}\n${files}${patch}`;
}

// The message is generated in a child session with an empty history, so the
// parent conversation is not part of its context. `generate` adds no messages
// and runs no tools.
async function generateMessage(ctx, parent, prompt) {
  const child = await ctx.session.create({
    parentID: parent.id,
    agent: parent.agent,
    model: parent.model,
    title: "squash message",
  });
  try {
    return commitMessage(await ctx.session.generate({ sessionID: child.id, prompt }));
  } finally {
    await ctx.session.remove({ sessionID: child.id }).catch(() => {});
  }
}

async function commitSquash(ctx, parent, directory, former) {
  const { diff, error } = await stagedDiff(directory);
  if (error) return finish(ctx, directory, former, "", error);

  let message;
  try {
    message = await generateMessage(ctx, parent, withDiff(diff, await stagedStat(directory)));
  } catch (error) {
    return finish(ctx, directory, former, "", `message generation failed: ${error?.message ?? error}`);
  }
  if (!message) return finish(ctx, directory, former, "", "no commit message");
  return finish(ctx, directory, former, message);
}

// start has already reset the branch, so a missing Former HEAD line must still
// restore. `git reset --hard` reflogs the tip it left, which is that HEAD.
async function formerFromReflog(directory) {
  const result = await run("git", ["rev-parse", "--verify", "-q", "HEAD@{1}"], { cwd: directory });
  return result.status === 0 ? (result.stdout ?? "").trim() : "";
}

async function squash(ctx, sessionID) {
  const parent = await ctx.session.get({ sessionID });
  const directory = parent.location.directory;

  const start = await run("bash", [scriptPath(ctx, "squash-start.sh")], { cwd: directory });
  const startOutput = outputOf(start);
  if (!startOutput.startsWith("SQUASH: OK")) return failure(start);

  const former = /^Former HEAD: ([0-9a-f]+)$/m.exec(startOutput)?.[1];
  if (!former) {
    const recovered = await formerFromReflog(directory);
    if (!recovered) {
      return "SQUASH: FAIL (no former HEAD; branch is reset to origin/main with staged changes)";
    }
    return finish(ctx, directory, recovered, "", "no former HEAD");
  }

  try {
    return await commitSquash(ctx, parent, directory, former);
  } catch (error) {
    return finish(ctx, directory, former, "", `unexpected error: ${error?.message ?? error}`);
  }
}

export default {
  id: "squash-command",
  async setup(ctx) {
    await ctx.command.transform((editor) => {
      editor.add({
        name: "squash",
        description: "Squash the branch onto origin/main and rewrite its message",
        execute: async ({ sessionID, delivery }) => {
          let output;
          try {
            output = await squash(ctx, sessionID);
          } catch (error) {
            output = `SQUASH: FAIL (${error?.message ?? error})`;
          }
          try {
            await ctx.session.prompt({
              sessionID,
              text: output,
              delivery,
              resume: false,
            });
          } catch {
            // The squash is already committed; a failed post must not fail the command.
          }
        },
      });
    });
  },
};
