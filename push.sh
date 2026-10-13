#!/usr/bin/env bash
# One-tap push for Cute Pet Overlay — just run:  bash push.sh
set -e
cd "$(dirname "$0")"

if ! command -v git >/dev/null 2>&1; then
  echo "Installing git..."
  pkg install -y git
fi

git config --global --add safe.directory "$(pwd)" 2>/dev/null || true
git config --global --add safe.directory /storage/emulated/0/opencode/cute-pet-overlay 2>/dev/null || true

[ -d .git ] || git init -b main
git config user.name "${GIT_NAME:-cutepet}" 2>/dev/null || true
git config user.email "${GIT_EMAIL:-cutepet@mail.com}" 2>/dev/null || true

echo ""
echo "=== 1/3: Files ready ==="
git add -A
git status --short | head -n 30
git commit -m "cute pet v1" 2>/dev/null || echo "(already committed)"

echo ""
echo "=== 2/3: GitHub login ==="
echo "Create EMPTY repo first: github.com -> + -> New repository -> name: cute-pet-overlay -> Create"
echo ""
printf "GitHub username: "
read GH_USER
printf "Repo name [cute-pet-overlay]: "
read GH_REPO
GH_REPO=${GH_REPO:-cute-pet-overlay}
printf "Paste token (ghp_... from Settings > Developer settings > Tokens classic > repo): "
read -s GH_TOKEN
echo ""

git remote remove origin 2>/dev/null || true
git remote add origin "https://${GH_TOKEN}@github.com/${GH_USER}/${GH_REPO}.git"
git branch -M main

echo ""
echo "=== 3/3: Pushing... ==="
git push -u origin main

echo ""
echo "DONE! Now open github.com/${GH_USER}/${GH_REPO} -> Actions -> Build APK -> Run workflow"
echo "Wait ~4 min for green tick -> tap run -> Artifacts -> download cute-pet-debug"
