/**
 * Covered by APR01_ExportInvoiceREST_TEST
 * @author PSIN
 * @modifier VotreNom // Ajoutez votre nom et date de modification
 * Added differentiated VAT 5.5% handling.
 */
public class AP14_CommonExportInvoice {

    // ... (propriétés existantes : mode, invoices, dateOfIssueStart, etc.)
    public String mode{get;set;}
    public List<Invoice__c> invoices {get;set;}
    public Date dateOfIssueStart {get;set;}
    public Date dateOfIssueEnd {get;set;}
    public ID entityId {get;set;}
    public Boolean excludePrevTransfer {get;set;}
    public static final String nothing = ' ';
    public List<AccountingColumnSetting> lsExpCLIENT {get;set;}
    public List<AccountingColumnSetting> lsExpECRITURE {get;set;}
    public String fileName {get;set;}
    public String txtString {get;set;}
    // --- AJOUT : Constantes pour les types de ligne pour la lisibilité ---
    private static final String LINETYPE_CLIENT = 'CLIENT';
    private static final String LINETYPE_TTC = 'TTC';
    private static final String LINETYPE_DUEDATE = 'DUEDATE';
    private static final String LINETYPE_HT = 'HT';
    private static final String LINETYPE_ANALYTIC = 'ANALYTIC';
    private static final String LINETYPE_TVA = 'TVA';
    // --- FIN AJOUT ---

    // constructor
    public AP14_CommonExportInvoice(String mode, Date dateOfIssueStart, Date dateOfIssueEnd, ID entityId, Boolean excludePrevTransfer){
        this.mode = mode;
        // Correction : Ne pas assigner this.invoices à invoices ici, il sera peuplé par initInvoices
        // this.invoices = invoices;
        this.dateOfIssueStart = dateOfIssueStart;
        this.dateOfIssueEnd = dateOfIssueEnd;
        this.entityId = entityId;
        this.excludePrevTransfer = excludePrevTransfer;

        // TODO: Déterminer le franchiseur basé sur entityId pour charger les bons settings si nécessaire
        // Pour l'instant, on garde le comportement actuel (WSE hardcodé)
        this.initAccountingSettings();
    }

    public void initInvoices(){
        // Assurez-vous que tous les champs nécessaires sont ici, notamment :
        // AmountVAT20__c, AmountVAT55__c, AccountingCompteTVA__c, Accounting_Compte_TVA55__c
        this.invoices = [SELECT
                            Id, Name, AmountTaxExcluded__c, AmountTaxIncluded__c,
                            AmountVAT__c, // Gardé pour compatibilité potentielle, mais préférer 20/55
                            DateIssue__c, ContractLegalEntity__c, Contract__r.LegalEntity__c,
                            LegalEntity__c, RecordType.Name, RecordType.DeveloperName,
                            AccountingTable9__c, AccountingNumeroRupture__c,
                            AccountingCompteCollectif__c, AccountingCompteAnalytique__c,
                            AccountingCompteGeneral__c,
                            AccountingCompteTVA__c,         // Compte TVA standard (20%)
                            Accounting_Compte_TVA55__c,     // Compte TVA spécifique 5.5%
                            AccountingLibelle__c, AccountingTypePiece__c,
                            AccountingIdWSEgroup__c, AccountingPostalCode__c,
                            AccountingRecipientName__c, AccountingStreet__c,
                            AccountingCity__c, PublicFinancer__c, PublicFinancer__r.Name,
                            NumberDueDates__c, DueDate__c, DueDateForExport__c,
                            WasExported__c, DateLastTransfer__c,
                            AccountingJournalVente__c,
                            AmountVAT20__c,                 // Montant TVA 20%
                            AmountVAT55__c,                 // Montant TVA 5.5%
                            NameWGE__c, Account_Assignment__c,
                            (SELECT Id, Date__c, Amount__c, AccountingPaymentMode__c, PaymentMode__c FROM InvoiceDueDates__r),
                            (SELECT Id, Amount__c, WSEcenter__c, AnalyticCode__c FROM InvoiceAnalyticItems__r),
                            (SELECT Id, AmountTaxExcluded__c, AmountTaxIncluded__c, Accounting_Compte_Produit__c FROM InvoiceLines__r) // Ajouté si la logique WSE par produit est nécessaire
                        FROM Invoice__c
                        WHERE
                            (WasExported__c = false OR WasExported__c =: (this.excludePrevTransfer ? false : true)) AND
                            DateIssue__c >=: this.dateOfIssueStart AND
                            DateIssue__c <=: this.dateOfIssueEnd AND
                            LegalEntity__c =: this.entityId AND
                            Status__c = 'Validated'
                        ORDER BY Name ASC];
    }

    public Boolean markAsTransfered(){
        if (this.invoices == null || this.invoices.isEmpty()) {
            return true; // Rien à marquer
        }
        for(Invoice__c i : this.invoices){
            i.DateLastTransfer__c = datetime.now();
            // --- AJOUT : Marquer aussi comme exporté ---
            i.WasExported__c = true;
            // --- FIN AJOUT ---
        }

        try{
            // --- AJOUT : Vérification FLS avant DML ---
             List<String> fieldsToCheck = new List<String>{'DateLastTransfer__c', 'WasExported__c'};
             Map<String, Schema.SObjectField> fieldMap = Schema.SObjectType.Invoice__c.fields.getMap();
             for(String field : fieldsToCheck) {
                 if (!fieldMap.get(field).getDescribe().isUpdateable()) {
                     System.debug(LoggingLevel.ERROR, 'Permissions FLS insuffisantes pour mettre à jour le champ ' + field + ' sur Invoice__c.');
                     // Gérer l'erreur - peut-être lever une exception personnalisée
                     // throw new MyAccessException('FLS Update Denied on Invoice__c.' + field);
                     return false; // Ou retourner false
                 }
             }
            // --- FIN AJOUT ---
            update this.invoices;
        }catch(Exception ex){
            System.debug(LoggingLevel.ERROR, 'Erreur lors du marquage des factures comme transférées : ' + ex.getMessage() + '\n' + ex.getStackTraceString());
            // TODO: Améliorer la gestion d'erreur (Exception personnalisée ou retour structuré)
            return false;
        }
        return true;
    }

    public string accountingExport(){
        // --- AJOUT : Initialiser les factures si elles ne le sont pas ---
        if (this.invoices == null) {
            this.initInvoices();
        }
        if (this.invoices == null || this.invoices.isEmpty()) {
             return '!'; // Retourner le caractère initial si aucune facture trouvée
        }
        // --- FIN AJOUT ---


        // nom du fichier à générer (si nécessaire dans cette classe)
        // LegalEntity__c le = [SELECT AccountingPrefix__c FROM LegalEntity__c WHERE Id = :this.entityId LIMIT 1];
        // if(le != null && le.AccountingPrefix__c != null) {
        //    this.fileName = (le.AccountingPrefix__c + '-' + system.now() + '.TRA').replace(' ','-');
        // } else {
        //    this.fileName = ('EXPORT-' + system.now() + '.TRA').replace(' ','-');
        // }

        Set<String> clients = new Set<String>();
        List<String> returnStrInit = new List<String>{'!'};
        List<String> returnStr = new List<String>();
        Set<id> idPublicFinancerToSetAccountingId = new set<Id>(); // Gardé pour la logique d'erreur potentielle

        for(Invoice__c inv : this.invoices){

            // Vérification Public Financer (simplifiée pour API)
            if(inv.RecordType.DeveloperName.contains('PublicFinancer') && String.isBlank(inv.AccountingIdWSEgroup__c)){
                // Gestion d'erreur plus robuste nécessaire pour API
                System.debug(LoggingLevel.ERROR, 'Export Comptable Erreur: Public Financer manque AccountingId. Invoice ID: ' + inv.Id + ', Public Financer: ' + inv.PublicFinancer__r.Name);
                return 'ERREUR: Le Public Financer \'' + inv.PublicFinancer__r.Name + '\' (Facture ' + inv.Name + ') n\'a pas de code comptable (AccountingIdWSEgroup__c).';
                // Alternative : Lancer une exception
                // throw new AccountingExportException('Le Public Financer \'' + inv.PublicFinancer__r.Name + '\' (Facture ' + inv.Name + ') n\'a pas de code comptable.');
            }

            // Ligne CLIENT (si pas déjà ajoutée)
            if(!clients.contains(inv.AccountingIdWSEgroup__c)){
                clients.add(inv.AccountingIdWSEgroup__c);
                returnStrInit.add(this.getLine(inv, LINETYPE_CLIENT, null, null, null)); // Appel mis à jour
            }

            // Ligne TTC ou Echéances
            if(inv.NumberDueDates__c == null || inv.NumberDueDates__c <= 1){
                returnStr.add(this.getLine(inv, LINETYPE_TTC, null, null, null)); // Appel mis à jour
            } else {
                if (inv.InvoiceDueDates__r != null) { // Ajouter une vérification null
                    for(InvoiceDueDate__c idd : inv.InvoiceDueDates__r){
                        returnStr.add(this.getLine(inv, LINETYPE_DUEDATE, null, idd, null)); // Appel mis à jour
                    }
                }
            }

            // TODO: Réintégrer la logique WSE spécifique si nécessaire (split par InvoiceItem)
            // Si la logique WSE de VFC14 (boucle sur InvoiceLines__r pour HT et Analytic) est requise ici:
            /*
            if (franchisor == 'WSE' && inv.InvoiceLines__r != null) { // Assumer que 'franchisor' est déterminé
                 Integer indexInvoiceItem = 0;
                 for(InvoiceItem__c invoiceItem : inv.InvoiceLines__r){
                     // Ligne HT par produit
                     returnStr.add(this.getLine(inv, LINETYPE_HT + '_' + indexInvoiceItem, null, null, invoiceItem, null)); // Nécessite modif getLine pour gérer 'HT_x' et 'invoiceItem'

                     // Lignes analytiques par produit (si la logique est par produit *et* par centre analytique)
                     if (inv.InvoiceAnalyticItems__r != null) {
                         for(InvoiceAnalyticItem__c iaa : inv.InvoiceAnalyticItems__r){
                             returnStr.add(this.getLine(inv, LINETYPE_ANALYTIC + '_' + indexInvoiceItem, iaa, null, invoiceItem, null)); // Nécessite modif getLine
                         }
                     }
                     indexInvoiceItem++;
                 }
            } else { // Logique standard (similaire WGE ou ancienne WSE)
                 // Ligne HT globale
                 returnStr.add(this.getLine(inv, LINETYPE_HT, null, null, null)); // Appel mis à jour

                 // Lignes analytiques globales
                 if (inv.InvoiceAnalyticItems__r != null) { // Ajouter une vérification null
                    for(InvoiceAnalyticItem__c iaa : inv.InvoiceAnalyticItems__r){
                        returnStr.add(this.getLine(inv, LINETYPE_ANALYTIC, iaa, null, null)); // Appel mis à jour
                    }
                 }
            }
            */
            // Pour l'instant, on garde la logique simple de AP14 initiale :
            // Ligne HT globale
            returnStr.add(this.getLine(inv, LINETYPE_HT, null, null, null)); // Appel mis à jour

            // Lignes analytiques globales
            if (inv.InvoiceAnalyticItems__r != null) { // Ajouter une vérification null
               for(InvoiceAnalyticItem__c iaa : inv.InvoiceAnalyticItems__r){
                   returnStr.add(this.getLine(inv, LINETYPE_ANALYTIC, iaa, null, null)); // Appel mis à jour
               }
            }
            // --- FIN Logique HT/Analytic ---


            // --- MODIFICATION : Gestion différenciée des lignes TVA ---
            // Ligne TVA Standard (20% ou taux principal)
            if(inv.AmountVAT20__c != null && inv.AmountVAT20__c != 0) { // Utiliser != 0 pour inclure les avoirs
                returnStr.add(this.getLine(inv, LINETYPE_TVA, null, null, false)); // Appel mis à jour, isTVA55 = false
            }

            // Ligne TVA Spécifique (5.5%)
            if(inv.AmountVAT55__c != null && inv.AmountVAT55__c != 0) { // Utiliser != 0 pour inclure les avoirs
                returnStr.add(this.getLine(inv, LINETYPE_TVA, null, null, true)); // Appel mis à jour, isTVA55 = true
            }
            // --- FIN MODIFICATION ---

        } // Fin boucle sur invoices

        returnStrInit.addAll(returnStr);
        this.txtString = String.join(returnStrInit, '\r\n'); // Utiliser \r\n directement pour CRLF
        System.debug('Content Length: '+this.txtString.length());
        // System.debug('Content : '+this.txtString); // Attention: Peut être très long pour le debug log
        return this.txtString;
    }

    // initialisation du custom settings
    public void initAccountingSettings(){
        // TODO: Rendre dynamique en fonction du franchiseur de l'entité légale this.entityId
        // Pour l'instant, hardcodé WSE comme dans le code original AP14
        String targetFranchisor = 'WSE'; // A déterminer dynamiquement
        /* Exemple pour déterminer dynamiquement:
        try {
             LegalEntity__c le = [SELECT Franchisor__c FROM LegalEntity__c WHERE Id = :this.entityId];
             if (le != null && String.isNotBlank(le.Franchisor__c)) {
                 targetFranchisor = le.Franchisor__c;
             }
        } catch (Exception e) {
             System.debug(LoggingLevel.WARN, 'Impossible de déterminer le franchiseur pour l\'entité ' + this.entityId + '. Utilisation de WSE par défaut.');
        }
        */

        this.lsExpCLIENT = new List<AccountingColumnSetting> ();
        this.lsExpECRITURE = new List<AccountingColumnSetting> ();
        System.debug('Debut de lecture des parametres pour franchiseur: ' + targetFranchisor);
        Map<Id, AccountingSettings__c> settingsMap = AccountingSettings__c.getAll(); // Obtenir toutes les settings une fois

        for(AccountingSettings__c e : settingsMap.values()){
            if(e.Franchisor__c == targetFranchisor) { // Filtrer par franchiseur
                 if(e.ExportType__c == LINETYPE_CLIENT){ // Utiliser constante
                     this.lsExpCLIENT.add(new AccountingColumnSetting(e));
                 }
                 if(e.ExportType__c == 'ECRITURE'){ // Garder ECRITURE ici car c'est la valeur dans le setting
                     this.lsExpECRITURE.add(new AccountingColumnSetting(e));
                 }
            }
        }
        System.debug('Fin de lecture des parametres. Client Settings: ' + lsExpCLIENT.size() + ', Ecriture Settings: ' + lsExpECRITURE.size());

        this.lsExpCLIENT.sort();
        this.lsExpECRITURE.sort();
    }

    // --- MODIFICATION : Overload getLine pour compatibilité ---
    private String getLine(Invoice__c inv, String lineType){
        // Appelle la version complète avec isTVA55 à null (ou false par défaut)
        return this.getLine(inv, lineType, null, null, null);
    }
     private String getLine(Invoice__c inv, String lineType, InvoiceAnalyticItem__c iaa){
        // Appelle la version complète avec isTVA55 à null (ou false par défaut)
        return this.getLine(inv, lineType, iaa, null, null);
    }
     private String getLine(Invoice__c inv, String lineType, InvoiceAnalyticItem__c iaa, InvoiceDueDate__c idd){
        // Appelle la version complète avec isTVA55 à null (ou false par défaut)
        return this.getLine(inv, lineType, iaa, idd, null);
    }
    // --- FIN MODIFICATION ---


    // --- MODIFICATION : Signature de getLine et logique interne ---
    private String getLine(Invoice__c inv, String lineType, InvoiceAnalyticItem__c iaa, InvoiceDueDate__c idd, Boolean isTVA55){

        String line = '';
        List<AccountingColumnSetting> lsAccSett;
        if(lineType == LINETYPE_CLIENT) // Utiliser constante
            lsAccSett = this.lsExpCLIENT;
        else
            lsAccSett = this.lsExpECRITURE;

        // Vérification si les settings sont chargés
        if (lsAccSett == null || lsAccSett.isEmpty()) {
             System.debug(LoggingLevel.ERROR, 'Erreur: Aucun AccountingSetting trouvé pour lineType=' + lineType + ' (CLIENT ou ECRITURE)');
             return 'ERREUR_SETTINGS_MANQUANTS_POUR_' + lineType; // Ou lancer une exception
        }


        String fieldName;
        sObject so;
        AccountingColumnFieldInfo fieldInfos;

        for(AccountingColumnSetting exp : lsAccSett){

            if(exp.datatype == 'DEFAULT'){ // VALEUR PAR DEFAUT : FIXE OU VIDE
                line += rightPad(exp.defaultValue, exp.length, nothing);
            } else{
                // --- MODIFICATION : Utiliser le lineType brut (sans _index) pour récupérer les infos de champ ---
                // Si la logique WSE par produit est réactivée, il faudra extraire le type de base ('HT', 'ANALYTIC') ici avant getFieldInfos
                String baseLineType = lineType;
                /* Exemple si la logique WSE par produit est active:
                if (lineType.contains(LINETYPE_HT + '_')) {
                    baseLineType = LINETYPE_HT;
                } else if (lineType.contains(LINETYPE_ANALYTIC + '_')) {
                    baseLineType = LINETYPE_ANALYTIC;
                }
                */
                fieldInfos = exp.getFieldInfos(baseLineType); // Utiliser baseLineType

                if(fieldInfos == null) {
                     System.debug(LoggingLevel.WARN, 'Aucune configuration de champ trouvée pour la colonne ' + exp.position + ' et le type de ligne ' + baseLineType + '. Utilisation d\'une valeur vide.');
                     line += rightPad('', exp.length, nothing);
                     continue; // Passer à la colonne suivante
                }

                // Sélectionner le bon SObject source
                if(fieldInfos.sObjectName == 'Invoice__c')
                    so = inv;
                else if(fieldInfos.sObjectName == 'InvoiceAnalyticItem__c')
                    so = iaa;
                else if(fieldInfos.sObjectName == 'InvoiceDueDate__c')
                    so = idd;
                // --- AJOUT : Gérer le cas où l'objet source attendu est null ---
                else if (fieldInfos.sObjectName == 'InvoiceItem__c') {
                    // so = iit; // Si la logique WSE par produit est active et iit est passé en paramètre
                    System.debug(LoggingLevel.WARN, 'La configuration demande InvoiceItem__c mais il n\'est pas géré dans cette version de getLine.');
                    so = null;
                } else {
                    System.debug(LoggingLevel.WARN, 'SObject source non reconnu ou non fourni pour : ' + fieldInfos.sObjectName);
                    so = null; // Ou assigner `inv` par défaut ? Préférable d'être strict.
                }

                if (so == null && fieldInfos.sObjectName != null) {
                    System.debug(LoggingLevel.WARN, 'SObject source (' + fieldInfos.sObjectName + ') est null pour la colonne ' + exp.position + ' sur la ligne type ' + lineType + '. Facture: ' + inv.Id);
                    // Mettre une valeur par défaut ou continuer ? Mettons une chaîne vide paddée.
                    line += rightPad('', exp.length, nothing);
                    continue;
                }
                // --- FIN AJOUT ---


                // Récupérer la valeur du champ
                try { // Ajouter un try-catch pour les erreurs potentielles de get()
                    String fName = fieldInfos.fieldName; // Nom du champ issu de la config

                    // --- MODIFICATION : Logique conditionnelle pour TVA ---
                    if(lineType == LINETYPE_TVA && fName != null) {
                        if(exp.datatype == 'DECIMAL2V') { // Si c'est le champ montant
                            // On suppose que la config pointe vers AmountVAT20__c ou un champ générique
                            // On choisit le bon champ basé sur le flag isTVA55
                            fName = (isTVA55 != null && isTVA55) ? 'AmountVAT55__c' : 'AmountVAT20__c';
                        } else if (exp.datatype == 'TEXT') { // Si c'est le champ compte
                             // On suppose que la config pointe vers AccountingCompteTVA__c ou un champ générique
                             // On choisit le bon champ basé sur le flag isTVA55
                             fName = (isTVA55 != null && isTVA55) ? 'Accounting_Compte_TVA55__c' : 'AccountingCompteTVA__c';
                        }
                         // Si la configuration pointe *déjà* vers le bon champ (ex: SFFieldTVA__c = Invoice__c-AmountVAT55__c),
                         // alors cette logique d'override n'est pas nécessaire, mais elle est plus robuste
                         // si la config est moins spécifique.
                    }
                    // --- FIN MODIFICATION ---

                    // --- AJOUT : Vérification si le champ existe avant .get() ---
                    if (fName != null && so.getSObjectType().getDescribe().fields.getMap().containsKey(fName.toLowerCase())) {
                    // --- FIN AJOUT ---

                        if(exp.datatype == 'DATE'){
                            Date d;
                            if(fName != null){
                                d = (Date) so.get(fName);
                            }else{ d = System.today(); }
                            if (d != null) { // Ajouter check null avant format
                                line += DateTime.newInstance(d, Time.newInstance(12, 0, 0, 0)).format('ddMMyyyy');
                            } else {
                                line += rightPad('', exp.length, nothing); // Gérer date nulle
                            }

                        } else if(exp.datatype == 'DECIMAL'){
                            Decimal d;
                            if(fName != null) d = (Decimal) so.get(fName);
                            else d = 0;
                            String m = (d != null) ? String.valueOf(Math.abs(d)).replace('.','') : '';
                            line += leftPad(m, exp.length, '0');

                        } else if(exp.datatype == 'DECIMAL2V'){
                            Decimal d;
                            if(fName != null) d = (Decimal) so.get(fName);
                            else d = 0.00;
                            String m = (d != null) ? String.valueOf(Math.abs(d)).replace('.',',') : '0,00'; // Valeur par défaut si null
                            line += leftPad(m, exp.length, nothing );

                        } else if(exp.datatype == 'DECIMAL4V'){
                             Decimal d;
                             if(fName != null) d = (Decimal) so.get(fName);
                             else d = 0.0000;
                             String m = (d != null) ? String.valueOf(Math.abs(d)).replace('.',',') : '0,0000'; // Valeur par défaut si null
                             // line += line += leftPad(m, exp.length, nothing ); // Erreur : line += line +=
                             line += leftPad(m, exp.length, nothing );


                        } else if(exp.datatype == 'TEXT'){
                            String s = '';
                            if(fName != null){
                                Object fieldValue = so.get(fName); // Récupérer la valeur
                                if (fieldValue != null) { // Vérifier si la valeur est nulle
                                    s = String.valueOf(fieldValue); // Convertir en String
                                     if(exp.removeSpecialCharacters == true) {
                                        // Assurez-vous que MISC_VFCCommon est accessible ou réimplémentez la logique ici
                                        // Exemple simple (peut nécessiter une logique plus complète):
                                        s = s.toUpperCase().normalizeSpace()
                                             .replaceAll('[ÀÁÂÃÄÅĀ]', 'A').replaceAll('[ÈÉÊËĒ]', 'E')
                                             .replaceAll('[ÌÍÎÏĪ]', 'I').replaceAll('[ÒÓÔÕÖŌ]', 'O')
                                             .replaceAll('[ÙÚÛÜŪ]', 'U').replaceAll('Ç', 'C')
                                             .replaceAll('Ñ', 'N').replaceAll('[^A-Z0-9 /\\-\\.]', ''); // Garde lettres, chiffres, espace, /-.
                                    }
                                }
                            }
                            line += rightPad(s.left(exp.length), exp.length, nothing);

                        } else if(exp.datatype == 'DEBIT/CREDIT'){
                            // --- MODIFICATION : Utiliser constantes et vérifier null RecordType ---
                            Boolean isCreditNote = inv.RecordType != null && inv.RecordType.DeveloperName != null && inv.RecordType.DeveloperName.contains('CreditNote');
                            Boolean isInvoice = inv.RecordType != null && inv.RecordType.DeveloperName != null && inv.RecordType.DeveloperName.contains('Invoice');

                            if((isCreditNote && baseLineType == LINETYPE_TTC) ||
                               (isInvoice && (baseLineType == LINETYPE_HT || baseLineType == LINETYPE_TVA || baseLineType == LINETYPE_ANALYTIC))){
                                line += 'C';
                            }else{
                                line += 'D';
                            }
                            // --- FIN MODIFICATION ---

                        } else if(exp.datatype == 'AUX/ANALYTIC'){
                             // --- MODIFICATION : Utiliser constantes ---
                            if(baseLineType == LINETYPE_TTC || baseLineType == LINETYPE_DUEDATE){
                                line += 'X';
                            } else if(baseLineType == LINETYPE_ANALYTIC){
                                line += 'A';
                            } else {
                                line += nothing;
                            }
                            // --- FIN MODIFICATION ---
                        } else {
                             System.debug(LoggingLevel.WARN, 'DataType inconnu: ' + exp.datatype + ' pour colonne ' + exp.position);
                             line += rightPad('', exp.length, nothing);
                        }
                    // --- AJOUT : Fin du bloc if (fName != null && champ existe) ---
                    } else {
                         System.debug(LoggingLevel.WARN, 'Champ ' + fName + ' non trouvé sur SObject ' + (so != null ? so.getSObjectType() : 'null') + ' ou fieldName est null.');
                         line += rightPad('', exp.length, nothing);
                    }
                    // --- FIN AJOUT ---

                } catch (Exception e) {
                    System.debug(LoggingLevel.ERROR, 'Erreur lors de la récupération/formatage de la valeur pour la colonne ' + exp.position + ' (Champ: ' + fieldInfos.fieldName + ', Type Ligne: ' + lineType + ', Facture: ' + inv.Id + ') - Erreur: ' + e.getMessage() + '\n' + e.getStackTraceString());
                    // Ajouter une valeur par défaut ou une indication d'erreur dans la ligne ?
                    line += rightPad('ERROR', exp.length, nothing).left(exp.length); // Indiquer erreur dans le fichier
                }
            } // Fin else (datatype != DEFAULT)
        } // Fin boucle sur colonnes (lsAccSett)
        return line;
    }
    // --- FIN MODIFICATION ---


    // Méthodes rightPad et leftPad (inchangées)
    private String rightPad(String value, Integer length, String padWith){
         if (value == null) value = '';
         // Échapper les espaces avant le padding pour les préserver
         return value.replace(' ', '¤').rightPad(length, padWith).replace(padWith, (padWith == ' ' ? ' ' : padWith)).replace('¤', ' ');
    }

    private String leftPad(String value, Integer length, String padWith){
        if (value == null) value = '';
         // Échapper les espaces avant le padding pour les préserver
        return value.replace(' ', '¤').leftPad(length, padWith).replace(padWith, (padWith == ' ' ? ' ' : padWith)).replace('¤', ' ');
    }

    // Classes Wrapper (AccountingColumnSetting, AccountingColumnFieldInfo) - inchangées
    public class AccountingColumnSetting implements Comparable{
        public String dataType;
        public Integer length;
        public Decimal position; // Garder privé si possible
        public Boolean removeSpecialCharacters;
        public String defaultValue;

        private Map<String, AccountingColumnFieldInfo> fieldsToUseByLineType;

        public AccountingColumnSetting(AccountingSettings__c s){
            this.dataType = s.DataType__c;
            this.length = Integer.valueOf(s.Size__c);
            this.position = s.Position__c;
            this.removeSpecialCharacters = s.RemoveSpecialCharacters__c;
            this.defaultValue = (String.isBlank(s.DefaultValue__c) ? AP14_CommonExportInvoice.nothing : s.DefaultValue__c); // Utiliser la constante de la classe externe

            if(this.dataType != 'DEFAULT'){
                // --- MODIFICATION : Utiliser les constantes ---
                this.fieldsToUseByLineType = new Map<String, AccountingColumnFieldInfo>();
                if(s.ExportType__c == AP14_CommonExportInvoice.LINETYPE_CLIENT){ // Utiliser constante
                    if(String.isNotBlank(s.SFfieldCLIENT__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_CLIENT, new AccountingColumnFieldInfo(s.SFfieldCLIENT__c));
                }
                else if (s.ExportType__c == 'ECRITURE'){ // La valeur dans le setting est 'ECRITURE'
                    if(String.isNotBlank(s.SFfieldTTC__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_TTC, new AccountingColumnFieldInfo(s.SFfieldTTC__c));
                    if(String.isNotBlank(s.SFfieldDUEDATE__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_DUEDATE, new AccountingColumnFieldInfo(s.SFfieldDUEDATE__c));
                    if(String.isNotBlank(s.SFfieldHT__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_HT, new AccountingColumnFieldInfo(s.SFfieldHT__c));
                    if(String.isNotBlank(s.SFfieldANALYTIC__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_ANALYTIC, new AccountingColumnFieldInfo(s.SFfieldANALYTIC__c));
                    if(String.isNotBlank(s.SFfieldTVA__c)) this.fieldsToUseByLineType.put(AP14_CommonExportInvoice.LINETYPE_TVA, new AccountingColumnFieldInfo(s.SFfieldTVA__c));
                }
                // --- FIN MODIFICATION ---
            }
        }

        public AccountingColumnFieldInfo getFieldInfos(String lineType){
            // --- AJOUT : Vérification null ---
            return (fieldsToUseByLineType != null) ? fieldsToUseByLineType.get(lineType) : null;
            // --- FIN AJOUT ---
        }

        public Integer compareTo(Object compareTo) {
            AccountingColumnSetting compareToCom = (AccountingColumnSetting) compareTo;
            Integer returnValue = 0;
            // --- AJOUT : Gestion des positions nulles ---
            Decimal p1 = this.position == null ? 99999 : this.position;
            Decimal p2 = compareToCom.position == null ? 99999 : compareToCom.position;
            if (p1 > p2) returnValue = 1;
            else if (p1 < p2) returnValue = -1;
            // --- FIN AJOUT ---
            return returnValue;
        }
    }

    public class AccountingColumnFieldInfo{
        public String sObjectName;
        public String fieldName;

        public AccountingColumnFieldInfo(String sObjectAndFieldName){
            if(String.isNotBlank(sObjectAndFieldName) && sObjectAndFieldName.contains('-')){ // Ajout vérif contient '-'
                this.sObjectName = sObjectAndFieldName.substringBefore('-');
                this.fieldName = sObjectAndFieldName.substringAfter('-');
            } else {
                 System.debug(LoggingLevel.WARN, 'Format SObjectAndFieldName invalide ou vide : ' + sObjectAndFieldName);
                 // Laisser les champs à null
            }
        }
    }

    // --- AJOUT : Exception Personnalisée (Exemple) ---
    public class AccountingExportException extends Exception {}
    // --- FIN AJOUT ---
}
