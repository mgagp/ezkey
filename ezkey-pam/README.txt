# ==================================================
# Instructions d'utilisation
# ==================================================

# INSTRUCTIONS D'UTILISATION:
#
# 1. Créer la structure des fichiers:
#    mkdir pam-ezkey-docker
#    cd pam-ezkey-docker
#    
# 2. Créer tous les fichiers listés ci-dessus
# 
# 3. Copier les sources PAM depuis l'artifact précédent dans les dossiers appropriés
#
# 4. Rendre les scripts exécutables:
#    chmod +x build-and-run.sh docker-entrypoint.sh test-in-docker.sh
#
# 5. Lancer l'environnement:
#    ./build-and-run.sh
#
# 6. Tester:
#    ssh -p 2222 testuser@localhost
#
# 7. Voir les logs:
#    docker exec ezkey-pam-test tail -f /var/log/secure