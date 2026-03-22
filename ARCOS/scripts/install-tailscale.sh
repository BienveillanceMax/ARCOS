#!/usr/bin/env bash
# install-tailscale.sh — Installe Tailscale sur le Raspberry Pi pour accès distant au calendrier CalDAV
# Usage: sudo bash scripts/install-tailscale.sh

set -euo pipefail

echo "=== Installation de Tailscale ==="

# Vérifie qu'on est root
if [ "$(id -u)" -ne 0 ]; then
    echo "ERREUR: Ce script doit être lancé avec sudo."
    exit 1
fi

# Installe Tailscale via le script officiel
if command -v tailscale &> /dev/null; then
    echo "Tailscale est déjà installé."
    tailscale version
else
    echo "Installation de Tailscale..."
    curl -fsSL https://tailscale.com/install.sh | sh
fi

# Active et démarre le service
systemctl enable --now tailscaled

# Lance l'authentification
echo ""
echo "=== Authentification Tailscale ==="
echo "Un lien va s'afficher. Ouvrez-le sur un appareil avec navigateur pour connecter ce Pi."
echo ""
tailscale up

# Affiche le statut
echo ""
echo "=== Statut Tailscale ==="
tailscale status
echo ""
TAILSCALE_IP=$(tailscale ip -4 2>/dev/null || echo "non disponible")
echo "IP Tailscale: ${TAILSCALE_IP}"
echo ""
echo "=== Configuration DAVx5 (téléphone) ==="
echo "1. Installez DAVx5 depuis le Play Store / F-Droid"
echo "2. Ajoutez un compte CalDAV avec l'URL: http://${TAILSCALE_IP}:5232/arcos/calendar/"
echo "3. Identifiants: arcos / arcos"
echo "4. Synchronisez et le calendrier apparaîtra dans votre app calendrier"
