#!/bin/bash
cd ~/public_html || exit 1
mkdir -p ~/.ssh
chmod 700 ~/.ssh
[ -f ~/.ssh/github_deploy ] || ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N ""
echo "=== CLAVE PUBLICA ==="
cat ~/.ssh/github_deploy.pub
echo "=== ARCHIVOS CON POSIBLES CREDENCIALES ==="
grep -rIl -i -E "password|passwd|db_pass|secret|api_key" . | head -50
echo "=== FIN ==="
