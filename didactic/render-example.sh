#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
Usage:
  ./render-example.sh --config SCENARIO_RUN_YAML [options]

Options:
  --template-slug SLUG     access-login-full | contextual-approval-full | trace-index
  --artifacts-dir DIR      Defaults to <scenario dir>/artifacts
  --output PATH            Defaults to <artifacts dir>/article.<slug>.generated.md
  --run                    Execute protocol_lab run before rendering
  --skip-admin-wait        Pass through to protocol_lab run
  --full-transcript        Include full local-lab secrets in rendered markdown (default)
  --redacted               Render without --full-transcript
  --list-templates         Print known template slugs and exit
  -h, --help               Show this help

Examples:
  ./render-example.sh --config scenarios/cleanstart-example/run.example.yaml --run
  ./render-example.sh --config scenarios/foo/run.yaml --template-slug contextual-approval-full
EOF
}

resolve_template() {
  case "$1" in
    access-login-full) echo "$ROOT_DIR/templates/access-login/article-full.md" ;;
    contextual-approval-full) echo "$ROOT_DIR/templates/contextual-approval/article-full.md" ;;
    trace-index) echo "$ROOT_DIR/templates/shared/trace-index.md" ;;
    *)
      echo "Unknown template slug: $1" >&2
      return 1
      ;;
  esac
}

print_templates() {
  cat <<'EOF'
access-login-full        templates/access-login/article-full.md
contextual-approval-full templates/contextual-approval/article-full.md
trace-index              templates/shared/trace-index.md
EOF
}

template_slug="access-login-full"
config_path=""
artifacts_dir=""
output_path=""
do_run=false
skip_admin_wait=false
full_transcript=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --template-slug)
      template_slug="$2"
      shift 2
      ;;
    --config)
      config_path="$2"
      shift 2
      ;;
    --artifacts-dir)
      artifacts_dir="$2"
      shift 2
      ;;
    --output)
      output_path="$2"
      shift 2
      ;;
    --run)
      do_run=true
      shift
      ;;
    --skip-admin-wait)
      skip_admin_wait=true
      shift
      ;;
    --full-transcript)
      full_transcript=true
      shift
      ;;
    --redacted)
      full_transcript=false
      shift
      ;;
    --list-templates)
      print_templates
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$config_path" ]]; then
  echo "--config is required" >&2
  usage >&2
  exit 1
fi

config_abs="$(cd "$(dirname "$config_path")" && pwd)/$(basename "$config_path")"
if [[ ! -f "$config_abs" ]]; then
  echo "Config not found: $config_abs" >&2
  exit 1
fi

if [[ -z "$artifacts_dir" ]]; then
  artifacts_dir="$(dirname "$config_abs")/artifacts"
fi

mkdir -p "$artifacts_dir"

template_path="$(resolve_template "$template_slug")"
if [[ -z "$output_path" ]]; then
  output_path="$artifacts_dir/article.${template_slug}.generated.md"
fi

if $do_run; then
  run_args=(python -m protocol_lab run --config "$config_abs" --artifacts-dir "$artifacts_dir")
  if $skip_admin_wait; then
    run_args+=(--skip-admin-wait)
  fi
  "${run_args[@]}"
fi

render_args=(python -m protocol_lab render --template "$template_path" --artifacts-dir "$artifacts_dir" --output "$output_path")
if $full_transcript; then
  render_args+=(--full-transcript)
fi
"${render_args[@]}"

echo "Rendered $output_path using $template_slug"
