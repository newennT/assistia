package com.assistia.poc.rag;

import java.util.List;

public final class DemoCorpus {
    private DemoCorpus() {
    }

    public static List<DemoDocument> documents() {
        return List.of(
                new DemoDocument(1, "DNS — résolution et diagnostic",
                        "Le DNS traduit un nom de domaine en adresse IP. Sous Windows, "
                                + "utiliser nslookup nom.example et ipconfig /all pour vérifier le serveur DNS reçu. "
                                + "Sous Linux avec systemd-resolved, utiliser resolvectl status et "
                                + "resolvectl query nom.example. Comparer le serveur configuré avec le serveur attendu."),
                new DemoDocument(2, "DHCP — attribution d'adresses IP",
                        "DHCP attribue automatiquement une adresse IP, une passerelle et des serveurs DNS. "
                                + "Sous Windows, ipconfig /all affiche le bail et le serveur DHCP; "
                                + "ipconfig /release puis ipconfig /renew demande un nouveau bail. "
                                + "Sous Linux, nmcli device show affiche les paramètres reçus. "
                                + "Si aucun bail n'est obtenu, vérifier la portée DHCP et la connectivité au réseau local."),
                new DemoDocument(3, "VPN — diagnostic de connexion",
                        "Si le client VPN refuse la connexion, vérifier d'abord que l'accès Internet fonctionne, "
                                + "que le profil et l'adresse du serveur VPN sont corrects, et que l'horloge du poste "
                                + "est synchronisée. Vérifier ensuite les identifiants, la validation MFA et l'état "
                                + "du client. Consulter le message d'erreur et les journaux avant toute modification "
                                + "du pare-feu ou des règles de sécurité."),
                new DemoDocument(4, "Windows — diagnostic réseau",
                        "Pour diagnostiquer le réseau sous Windows, exécuter ipconfig /all pour voir l'adresse IP, "
                                + "la passerelle, le serveur DHCP et les DNS; Get-NetAdapter pour vérifier l'état "
                                + "des interfaces; puis Test-NetConnection hôte -Port 443 pour tester une connexion "
                                + "TCP ciblée. Un test réussi vers une adresse IP mais échoué par nom peut orienter "
                                + "vers le DNS."),
                new DemoDocument(5, "Linux — vérification des services réseau",
                        "Sous Linux, ip addr et ip route affichent les adresses et la route par défaut. "
                                + "systemctl status NetworkManager vérifie le service NetworkManager; "
                                + "systemctl status systemd-resolved vérifie le résolveur local. "
                                + "resolvectl status montre la configuration DNS. Un service arrêté ou en échec "
                                + "doit être confirmé dans ses journaux avant intervention."),
                new DemoDocument(6, "Imprimantes réseau — connectivité IP",
                        "Pour une imprimante réseau inaccessible, vérifier qu'elle est allumée et reliée au bon "
                                + "réseau, puis relever son adresse IP sur son panneau ou sa page de configuration. "
                                + "Tester la connectivité avec ping et vérifier que l'adresse n'a pas changé dans "
                                + "la file d'impression ou le port configuré sur le poste. Si le protocole RAW est "
                                + "utilisé, Test-NetConnection adresse_ip -Port 9100 peut tester le port depuis Windows."));
    }
}
