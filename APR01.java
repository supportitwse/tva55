@isTest
private class APR01_ExportInvoiceREST_TEST {

    // == Données de Test Communes ==
    @testSetup
    static void makeData(){
        // --- Configuration Comptable (Exemple Minimaliste) ---
        // Adaptez ces settings pour correspondre EXACTEMENT à votre configuration réelle
        List<AccountingSettings__c> settings = new List<AccountingSettings__c>();
        // CLIENT Lines
        settings.add(new AccountingSettings__c(Name='C1', Franchisor__c='WSE', ExportType__c='CLIENT', Position__c=1, DataType__c='TEXT', Size__c=10, SFfieldCLIENT__c='Invoice__c-AccountingIdWSEgroup__c'));
        settings.add(new AccountingSettings__c(Name='C2', Franchisor__c='WSE', ExportType__c='CLIENT', Position__c=2, DataType__c='TEXT', Size__c=30, SFfieldCLIENT__c='Invoice__c-AccountingRecipientName__c', RemoveSpecialCharacters__c = true));
        settings.add(new AccountingSettings__c(Name='C3', Franchisor__c='WSE', ExportType__c='CLIENT', Position__c=3, DataType__c='TEXT', Size__c=5, SFfieldCLIENT__c='Invoice__c-AccountingPostalCode__c'));
        // ECRITURE Lines (exemple TTC, HT, TVA 20, TVA 5.5)
        settings.add(new AccountingSettings__c(Name='E1', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=1, DataType__c='TEXT', Size__c=3, SFfieldTTC__c='Invoice__c-AccountingJournalVente__c', SFfieldHT__c='Invoice__c-AccountingJournalVente__c', SFfieldTVA__c='Invoice__c-AccountingJournalVente__c', SFfieldANALYTIC__c='Invoice__c-AccountingJournalVente__c', SFfieldDUEDATE__c='Invoice__c-AccountingJournalVente__c'));
        settings.add(new AccountingSettings__c(Name='E2', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=2, DataType__c='TEXT', Size__c=10, SFfieldTTC__c='Invoice__c-AccountingIdWSEgroup__c', SFfieldHT__c='Invoice__c-AccountingCompteGeneral__c', SFfieldTVA__c='Invoice__c-AccountingCompteTVA__c', SFfieldANALYTIC__c='InvoiceAnalyticItem__c-AnalyticCode__c', SFfieldDUEDATE__c='Invoice__c-AccountingIdWSEgroup__c')); // Note: Analytic utilise un autre objet/champ
        settings.add(new AccountingSettings__c(Name='E3', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=3, DataType__c='DATE', Size__c=8, SFfieldTTC__c='Invoice__c-DateIssue__c', SFfieldHT__c='Invoice__c-DateIssue__c', SFfieldTVA__c='Invoice__c-DateIssue__c', SFfieldANALYTIC__c='Invoice__c-DateIssue__c', SFfieldDUEDATE__c='InvoiceDueDate__c-Date__c'));
        settings.add(new AccountingSettings__c(Name='E4', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=4, DataType__c='DEBIT/CREDIT', Size__c=1));
        settings.add(new AccountingSettings__c(Name='E5', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=5, DataType__c='DECIMAL2V', Size__c=15, SFfieldTTC__c='Invoice__c-AmountTaxIncluded__c', SFfieldHT__c='Invoice__c-AmountTaxExcluded__c', SFfieldTVA__c='Invoice__c-AmountVAT20__c', SFfieldANALYTIC__c='InvoiceAnalyticItem__c-Amount__c', SFfieldDUEDATE__c='InvoiceDueDate__c-Amount__c')); // Note: la logique TVA 5.5 dans getLine utilisera AmountVAT55__c
        settings.add(new AccountingSettings__c(Name='E6', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=6, DataType__c='AUX/ANALYTIC', Size__c=1));
        settings.add(new AccountingSettings__c(Name='E7', Franchisor__c='WSE', ExportType__c='ECRITURE', Position__c=7, DataType__c='TEXT', Size__c=10, DefaultValue__c = 'DEFAULTVAL')); // Exemple de valeur par défaut

        // Assurer FLS pour les settings
        List<Schema.SObjectField> settingFields = new List<Schema.SObjectField>{
            AccountingSettings__c.Name, AccountingSettings__c.Franchisor__c, AccountingSettings__c.ExportType__c,
            AccountingSettings__c.Position__c, AccountingSettings__c.DataType__c, AccountingSettings__c.Size__c,
            AccountingSettings__c.SFfieldCLIENT__c, AccountingSettings__c.SFfieldTTC__c, AccountingSettings__c.SFfieldHT__c,
            AccountingSettings__c.SFfieldTVA__c, AccountingSettings__c.SFfieldANALYTIC__c, AccountingSettings__c.SFfieldDUEDATE__c,
            AccountingSettings__c.RemoveSpecialCharacters__c, AccountingSettings__c.DefaultValue__c
        };
        SObjectAccessDecision decision = Security.stripInaccessible(AccessType.CREATABLE, settings, settingFields);
        insert decision.getRecords();

        // --- Entité Légale ---
        LegalEntity__c le = new LegalEntity__c(Name='Test Entity WSE', Franchisor__c='WSE', AccountingPrefix__c='WSE');
        insert le;

        // --- Compte Client ---
        Account acc = new Account(Name='Test Customer');
        insert acc;

        // --- Contrat ---
        Contract__c contract = new Contract__c(AccountId=acc.Id, LegalEntity__c = le.Id, Status='Activated');
        insert contract;

        // --- Financeur Public ---
        PublicFinancer__c pfWithCode = new PublicFinancer__c(Name='PF OK', AccountingIdWSEgroup__c='PF001');
        PublicFinancer__c pfWithoutCode = new PublicFinancer__c(Name='PF NOK');
        insert new List<PublicFinancer__c>{pfWithCode, pfWithoutCode};

        // --- Record Types Facture (Adaptez les DeveloperName si nécessaire) ---
        Id rtInvoice = Schema.SObjectType.Invoice__c.getRecordTypeInfosByDeveloperName().get('Invoice').getRecordTypeId();
        Id rtCreditNote = Schema.SObjectType.Invoice__c.getRecordTypeInfosByDeveloperName().get('CreditNote').getRecordTypeId();
        Id rtPubFin = Schema.SObjectType.Invoice__c.getRecordTypeInfosByDeveloperName().get('PublicFinancerInvoice').getRecordTypeId();


        // --- Factures de Test ---
        List<Invoice__c> invoicesToInsert = new List<Invoice__c>();
        Date testDate = Date.newInstance(2023, 10, 26);

        // 1. Facture Standard (TVA 20% uniquement)
        invoicesToInsert.add(new Invoice__c(Name='INV-001', RecordTypeId=rtInvoice, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(1), AmountTaxExcluded__c=100, AmountVAT20__c=20, AmountVAT55__c=0, AmountTaxIncluded__c=120, AccountingIdWSEgroup__c='CUST001', AccountingRecipientName__c='Customer 1 ÉÀ', AccountingPostalCode__c='75001', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706000', AccountingCompteTVA__c='445710', WasExported__c = false));
        // 2. Facture (TVA 5.5% uniquement)
        invoicesToInsert.add(new Invoice__c(Name='INV-002', RecordTypeId=rtInvoice, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(1), AmountTaxExcluded__c=200, AmountVAT20__c=0, AmountVAT55__c=11, AmountTaxIncluded__c=211, AccountingIdWSEgroup__c='CUST002', AccountingRecipientName__c='Customer 2', AccountingPostalCode__c='75002', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706001', Accounting_Compte_TVA55__c='445712', WasExported__c = false));
        // 3. Facture (TVA Mixte)
        invoicesToInsert.add(new Invoice__c(Name='INV-003', RecordTypeId=rtInvoice, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(1), AmountTaxExcluded__c=300, AmountVAT20__c=60, AmountVAT55__c=16.5, AmountTaxIncluded__c=376.5, AccountingIdWSEgroup__c='CUST001', AccountingRecipientName__c='Customer 1 ÉÀ', AccountingPostalCode__c='75001', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706000', AccountingCompteTVA__c='445710', Accounting_Compte_TVA55__c='445712', NumberDueDates__c = 2, WasExported__c = false)); // Echéances multiples
        // 4. Avoir (Basé sur INV-001)
        invoicesToInsert.add(new Invoice__c(Name='AV-001', RecordTypeId=rtCreditNote, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate.addDays(1), DueDate__c=testDate.addDays(1), AmountTaxExcluded__c=-100, AmountVAT20__c=-20, AmountVAT55__c=0, AmountTaxIncluded__c=-120, AccountingIdWSEgroup__c='CUST001', AccountingRecipientName__c='Customer 1 ÉÀ', AccountingPostalCode__c='75001', AccountingJournalVente__c='AV', AccountingCompteGeneral__c='706000', AccountingCompteTVA__c='445710', WasExported__c = false));
        // 5. Facture Financeur Public OK
        invoicesToInsert.add(new Invoice__c(Name='INV-PF-01', RecordTypeId=rtPubFin, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, PublicFinancer__c = pfWithCode.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(2), AmountTaxExcluded__c=500, AmountVAT20__c=0, AmountVAT55__c=0, AmountTaxIncluded__c=500, AccountingIdWSEgroup__c=pfWithCode.AccountingIdWSEgroup__c, AccountingRecipientName__c=pfWithCode.Name, AccountingPostalCode__c='75003', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706002', WasExported__c = false));
        // 6. Facture Financeur Public NOK (sera filtrée par le test d'erreur)
        invoicesToInsert.add(new Invoice__c(Name='INV-PF-02', RecordTypeId=rtPubFin, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, PublicFinancer__c = pfWithoutCode.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(2), AmountTaxExcluded__c=600, AmountVAT20__c=0, AmountVAT55__c=0, AmountTaxIncluded__c=600, AccountingIdWSEgroup__c=null, AccountingRecipientName__c=pfWithoutCode.Name, AccountingPostalCode__c='75004', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706003', WasExported__c = false));
        // 7. Facture Déjà Exportée
        invoicesToInsert.add(new Invoice__c(Name='INV-004-EXP', RecordTypeId=rtInvoice, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate, DueDate__c=testDate.addMonths(1), AmountTaxExcluded__c=50, AmountVAT20__c=10, AmountVAT55__c=0, AmountTaxIncluded__c=60, AccountingIdWSEgroup__c='CUST003', AccountingRecipientName__c='Customer 3', AccountingPostalCode__c='75005', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706004', AccountingCompteTVA__c='445710', WasExported__c = true, DateLastTransfer__c = System.now().addDays(-1)));
        // 8. Facture hors période
         invoicesToInsert.add(new Invoice__c(Name='INV-OUT', RecordTypeId=rtInvoice, Status__c='Validated', LegalEntity__c=le.Id, Contract__c=contract.Id, Account__c = acc.Id, DateIssue__c=testDate.addMonths(-1), DueDate__c=testDate, AmountTaxExcluded__c=10, AmountVAT20__c=2, AmountVAT55__c=0, AmountTaxIncluded__c=12, AccountingIdWSEgroup__c='CUST004', AccountingRecipientName__c='Customer 4', AccountingPostalCode__c='75006', AccountingJournalVente__c='VT', AccountingCompteGeneral__c='706005', AccountingCompteTVA__c='445710', WasExported__c = false));

        insert invoicesToInsert;

        // --- Échéances pour INV-003 ---
        Invoice__c inv3 = [SELECT Id FROM Invoice__c WHERE Name='INV-003'];
        List<InvoiceDueDate__c> dueDates = new List<InvoiceDueDate__c>();
        dueDates.add(new InvoiceDueDate__c(Invoice__c=inv3.Id, Date__c=testDate.addMonths(1), Amount__c=200));
        dueDates.add(new InvoiceDueDate__c(Invoice__c=inv3.Id, Date__c=testDate.addMonths(2), Amount__c=176.5));
        insert dueDates;

        // --- Lignes Analytiques (Exemple pour INV-001) ---
         Invoice__c inv1 = [SELECT Id FROM Invoice__c WHERE Name='INV-001'];
         List<InvoiceAnalyticItem__c> analytics = new List<InvoiceAnalyticItem__c>();
         analytics.add(new InvoiceAnalyticItem__c(Invoice__c = inv1.Id, Amount__c = 60, AnalyticCode__c = 'ANA001'));
         analytics.add(new InvoiceAnalyticItem__c(Invoice__c = inv1.Id, Amount__c = 40, AnalyticCode__c = 'ANA002'));
         insert analytics;
    }

    // == Tests pour AP14_CommonExportInvoice ==

    @isTest static void testExportSuccess_AllVATTypes_MultiDueDates() {
        Date testDate = Date.newInstance(2023, 10, 26);
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];
        Invoice__c inv1 = [SELECT Id, Name, AccountingIdWSEgroup__c, AccountingRecipientName__c, AccountingPostalCode__c, AccountingJournalVente__c, AccountingCompteGeneral__c, AccountingCompteTVA__c, DateIssue__c, AmountTaxIncluded__c, AmountTaxExcluded__c, AmountVAT20__c FROM Invoice__c WHERE Name='INV-001'];
        Invoice__c inv2 = [SELECT Id, Name, AccountingIdWSEgroup__c, AccountingRecipientName__c, AccountingPostalCode__c, AccountingJournalVente__c, AccountingCompteGeneral__c, Accounting_Compte_TVA55__c, DateIssue__c, AmountTaxIncluded__c, AmountTaxExcluded__c, AmountVAT55__c FROM Invoice__c WHERE Name='INV-002'];
        Invoice__c inv3 = [SELECT Id, Name, AccountingIdWSEgroup__c, AccountingRecipientName__c, AccountingPostalCode__c, AccountingJournalVente__c, AccountingCompteGeneral__c, AccountingCompteTVA__c, Accounting_Compte_TVA55__c, DateIssue__c, AmountTaxIncluded__c, AmountTaxExcluded__c, AmountVAT20__c, AmountVAT55__c FROM Invoice__c WHERE Name='INV-003'];
        Invoice__c pfInv = [SELECT Id, Name, AccountingIdWSEgroup__c, AccountingRecipientName__c, AccountingPostalCode__c, AccountingJournalVente__c, AccountingCompteGeneral__c, DateIssue__c, AmountTaxIncluded__c, AmountTaxExcluded__c FROM Invoice__c WHERE Name='INV-PF-01'];
        List<InvoiceDueDate__c> inv3DueDates = [SELECT Date__c, Amount__c FROM InvoiceDueDate__c WHERE Invoice__c = :inv3.Id ORDER BY Date__c];
        List<InvoiceAnalyticItem__c> inv1Analytics = [SELECT Amount__c, AnalyticCode__c FROM InvoiceAnalyticItem__c WHERE Invoice__c = :inv1.Id ORDER BY AnalyticCode__c];


        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false); // excludePrevTransfer = false

        String exportResult = '';
        Test.startTest();
        exportResult = exporter.accountingExport();
        Test.stopTest();

        System.debug('Export Result:\n' + exportResult);
        System.assertEquals(false, String.isBlank(exportResult), 'Le résultat de l\'export ne doit pas être vide');
        System.assert(exportResult.startsWith('!'), 'Le résultat doit commencer par "!"');

        List<String> lines = exportResult.split('\r\n');
        // Calculer le nombre de lignes attendues :
        // 1 (header !)
        // + 3 clients distincts (CUST001, CUST002, PF001)
        // + INV-001: 1 TTC, 1 HT, 1 TVA20, 2 ANALYTIC = 5
        // + INV-002: 1 TTC, 1 HT, 1 TVA55 = 3
        // + INV-003: 2 DUEDATE, 1 HT, 1 TVA20, 1 TVA55 = 5
        // + INV-PF-01: 1 TTC, 1 HT = 2
        // Total = 1 + 3 + 5 + 3 + 5 + 2 = 19 lignes
        System.assertEquals(19, lines.size(), 'Nombre incorrect de lignes dans l\'export');

        // --- Vérifications Détaillées (Exemples - à adapter selon vos settings) ---
        // Ces assertions dépendent fortement des AccountingSettings__c exacts (position, taille, padding)
        // Vous devrez calculer les positions de début/fin pour chaque champ

        // Vérifier une ligne CLIENT (ex: CUST001)
        Boolean client1Found = false;
        for(String line : lines) {
            // Supposons que la position 1 (taille 10) contient le code client
            if (line.length() >= 10 && line.substring(0, 10).trim() == 'CUST001') {
                client1Found = true;
                // Supposons position 2 (taille 30) contient le nom (avec accents supprimés/majuscules)
                System.assert(line.length() >= 40 && line.substring(10, 40).trim() == 'CUSTOMER 1 EA', 'Nom client incorrect pour CUST001');
                // Supposons position 3 (taille 5) contient le code postal
                System.assert(line.length() >= 45 && line.substring(40, 45).trim() == '75001', 'Code postal incorrect pour CUST001');
                break;
            }
        }
        System.assert(client1Found, 'Ligne CLIENT pour CUST001 non trouvée');

         // Vérifier une ligne HT de INV-001
         // VT 706000     26102023C      100,00        DEFAULTVAL
         Boolean ht1Found = false;
         String expectedDate = Datetime.newInstance(testDate, Time.newInstance(12,0,0,0)).format('ddMMyyyy'); // Format date attendu
         for(String line : lines) {
             // Chercher une ligne commençant par VT, compte 706000, date, Crédit, montant 100,00
             // Ces vérifications dépendent des positions/tailles DANS LES SETTINGS !
             // Exemple simplifié basé sur les settings de makeData :
             // Pos 1 (Size 3): Journal 'VT '
             // Pos 2 (Size 10): Compte '706000    '
             // Pos 3 (Size 8): Date '26102023'
             // Pos 4 (Size 1): Sens 'C'
             // Pos 5 (Size 15): Montant '         100,00'
             // Pos 6 (Size 1): Aux/Ana ' '
             // Pos 7 (Size 10): Default 'DEFAULTVAL'
             if (line.startsWith('VT 706000    ' + expectedDate + 'C') && line.contains('100,00') && line.endsWith(' DEFAULTVAL')) {
                 ht1Found = true;
                 break;
             }
         }
        System.assert(ht1Found, 'Ligne HT pour INV-001 non trouvée ou incorrecte');

        // Vérifier la ligne TVA 5.5% de INV-002
        // VT 445712     26102023C       11,00        DEFAULTVAL
         Boolean tva55Found = false;
         for(String line : lines) {
              if (line.startsWith('VT 445712    ' + expectedDate + 'C') && line.contains('11,00') && line.endsWith(' DEFAULTVAL')) {
                 tva55Found = true;
                 break;
             }
         }
         System.assert(tva55Found, 'Ligne TVA 5.5% pour INV-002 non trouvée ou incorrecte');

         // Vérifier une ligne DUEDATE de INV-003
         Boolean dueDateFound = false;
         String expectedDueDate1 = Datetime.newInstance(inv3DueDates[0].Date__c, Time.newInstance(12,0,0,0)).format('ddMMyyyy');
         for(String line : lines) {
             // VT CUST001    DDMMYYYYD      200,00X       DEFAULTVAL (Date et Montant de la 1ere échéance)
             if (line.startsWith('VT CUST001   ' + expectedDueDate1 + 'D') && line.contains('200,00') && line.contains('X') && line.endsWith(' DEFAULTVAL')) {
                 dueDateFound = true;
                 break;
             }
         }
        System.assert(dueDateFound, 'Ligne DUEDATE pour INV-003 (1ere échéance) non trouvée ou incorrecte');

    }

    @isTest static void testExportSuccess_CreditNote() {
        Date testDate = Date.newInstance(2023, 10, 27); // Date de l'avoir
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];
        Invoice__c cn1 = [SELECT Id, Name FROM Invoice__c WHERE Name='AV-001'];

        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false);

        String exportResult = '';
        Test.startTest();
        exportResult = exporter.accountingExport();
        Test.stopTest();

        System.debug('Export Avoir Result:\n' + exportResult);
        List<String> lines = exportResult.split('\r\n');
        // 1 header + 1 client + 1 TTC + 1 HT + 1 TVA = 5 lignes
        System.assertEquals(5, lines.size(), 'Nombre incorrect de lignes pour l\'avoir');

        // Vérifier la ligne TTC de l'avoir (sens D)
        Boolean ttcCnFound = false;
        String expectedDate = Datetime.newInstance(testDate, Time.newInstance(12,0,0,0)).format('ddMMyyyy');
        for(String line : lines) {
             // AV CUST001    27102023D     -120,00X       DEFAULTVAL -> Montant doit être positif, sens D
             if (line.startsWith('AV CUST001   ' + expectedDate + 'D') && line.contains('120,00') && line.contains('X') && line.endsWith(' DEFAULTVAL')) {
                 ttcCnFound = true;
                 break;
             }
         }
        System.assert(ttcCnFound, 'Ligne TTC pour Avoir AV-001 non trouvée ou incorrecte (sens D attendu)');

         // Vérifier la ligne HT de l'avoir (sens C)
         Boolean htCnFound = false;
         for(String line : lines) {
             // AV 706000    27102023C     -100,00        DEFAULTVAL -> Montant doit être positif, sens C
             if (line.startsWith('AV 706000    ' + expectedDate + 'C') && line.contains('100,00') && !line.contains('X') && line.endsWith(' DEFAULTVAL')) {
                 htCnFound = true;
                 break;
             }
         }
         System.assert(htCnFound, 'Ligne HT pour Avoir AV-001 non trouvée ou incorrecte (sens C attendu)');
    }

    @isTest static void testExportError_PublicFinancerMissingCode() {
        Date testDate = Date.newInstance(2023, 10, 26);
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];
        // Inclure la facture PF-NOK dans la période
        Invoice__c pfNok = [SELECT Id, PublicFinancer__r.Name, Name FROM Invoice__c WHERE Name='INV-PF-02'];

        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false);

        String exportResult = '';
        Test.startTest();
         // On force l'initialisation des factures ici pour inclure PF-02
         exporter.initInvoices();
         exportResult = exporter.accountingExport();
        Test.stopTest();

        System.assert(exportResult.startsWith('ERREUR:'), 'Une erreur concernant le financeur public était attendue.');
        System.assert(exportResult.contains(pfNok.PublicFinancer__r.Name), 'Le message d\'erreur doit contenir le nom du financeur public.');
         System.assert(exportResult.contains(pfNok.Name), 'Le message d\'erreur doit contenir le nom de la facture.');
    }

     @isTest static void testExport_ExcludePrevious() {
        Date testDate = Date.newInstance(2023, 10, 26);
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];

        // excludePrevTransfer = true
        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, true);

        String exportResult = '';
        Test.startTest();
        exportResult = exporter.accountingExport();
        Test.stopTest();

        // Doit exclure INV-004-EXP (WasExported = true)
        System.assert(!exportResult.contains('CUST003'), 'La facture INV-004-EXP (CUST003) déjà exportée ne doit pas être incluse.');
     }

     @isTest static void testExport_IncludePrevious() {
        Date testDate = Date.newInstance(2023, 10, 26);
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];

        // excludePrevTransfer = false
        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false);

        String exportResult = '';
        Test.startTest();
        exportResult = exporter.accountingExport();
        Test.stopTest();

        // Doit inclure INV-004-EXP (WasExported = true car excludePrevTransfer = false)
        System.assert(exportResult.contains('CUST003'), 'La facture INV-004-EXP (CUST003) déjà exportée doit être incluse.');
    }

     @isTest static void testExport_NoInvoicesFound() {
         Date testDate = Date.newInstance(2023, 11, 01); // Date sans factures
         LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];

         AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false);

         String exportResult = '';
         Test.startTest();
         exportResult = exporter.accountingExport();
         Test.stopTest();

         System.assertEquals('!', exportResult, 'Doit retourner "!" si aucune facture n\'est trouvée.');
     }

     @isTest static void testMarkAsTransferred_Success() {
        Date testDate = Date.newInstance(2023, 10, 26);
        LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];
        List<Invoice__c> invoicesBefore = [SELECT Id, DateLastTransfer__c, WasExported__c FROM Invoice__c WHERE Name LIKE 'INV-%' AND Name != 'INV-OUT' AND Name != 'INV-PF-02']; // Prend les factures valides de la période
        System.assertNotEquals(0, invoicesBefore.size(), 'Au moins une facture doit être trouvée pour le test');
        for(Invoice__c inv : invoicesBefore) {
            System.assertEquals(null, inv.DateLastTransfer__c, 'DateLastTransfer__c doit être nulle avant le marquage pour ' + inv.Id);
            // System.assertEquals(false, inv.WasExported__c, 'WasExported__c doit être false avant le marquage pour ' + inv.Id); // Commenté car la ligne est commentée dans le code principal
        }

        AP14_CommonExportInvoice exporter = new AP14_CommonExportInvoice('API', testDate, testDate, le.Id, false);
        // Important: Il faut appeler initInvoices pour que exporter.invoices soit peuplé
        exporter.initInvoices();
        System.assert(exporter.invoices != null && !exporter.invoices.isEmpty(), 'Exporter.invoices ne doit pas être vide après initInvoices');


        Boolean result = false;
        Test.startTest();
        result = exporter.markAsTransfered();
        Test.stopTest();

        System.assertEquals(true, result, 'markAsTransfered doit retourner true');

        Set<Id> invoiceIds = new Map<Id, SObject>(invoicesBefore).keySet();
        List<Invoice__c> invoicesAfter = [SELECT Id, DateLastTransfer__c, WasExported__c FROM Invoice__c WHERE Id IN :invoiceIds];
        for(Invoice__c inv : invoicesAfter) {
            System.assertNotEquals(null, inv.DateLastTransfer__c, 'DateLastTransfer__c ne doit pas être nulle après le marquage pour ' + inv.Id);
             // System.assertEquals(true, inv.WasExported__c, 'WasExported__c doit être true après le marquage pour ' + inv.Id); // Commenté car la ligne est commentée dans le code principal
        }
     }

     // == Tests pour APR01_ExportInvoiceREST (Simulation) ==
     // Ces tests nécessitent que la classe APR01_ExportInvoiceREST existe réellement

     @isTest static void testRESTEndpoint_Success() {
         Date testDate = Date.newInstance(2023, 10, 26);
         LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];

         RestRequest req = new RestRequest();
         RestResponse res = new RestResponse();

         req.requestURI = '/services/apexrest/v1/exportInvoice'; // Adaptez l'URI
         req.httpMethod = 'GET'; // Ou POST selon votre implémentation
         // Ajouter les paramètres (adapter selon GET/POST)
         req.params.put('startDate', testDate.format());
         req.params.put('endDate', testDate.format());
         req.params.put('entityId', le.Id);
         req.params.put('excludePrevious', 'false');
         // Si POST, mettre les params dans req.requestBody

         RestContext.request = req;
         RestContext.response = res;

         Test.startTest();
         // Supposons que votre classe REST s'appelle APR01_ExportInvoiceREST
         // et qu'elle a une méthode statique @HttpGet/@HttpPost 'doExport'
         // APR01_ExportInvoiceREST.doExport(); // Décommentez et adaptez si la classe existe
         Test.stopTest();

         // --- Assertions sur la réponse REST (si la classe a été appelée) ---
         // System.assertEquals(200, RestContext.response.statusCode, 'Le statut HTTP doit être 200 OK');
         // System.assertEquals('text/plain', RestContext.response.headers.get('Content-Type'), 'Le Content-Type doit être text/plain');
         // System.assert(RestContext.response.headers.get('Content-Disposition').contains('.TRA'), 'Le Content-Disposition doit contenir le nom de fichier .TRA');
         // String responseBody = RestContext.response.responseBody != null ? RestContext.response.responseBody.toString() : '';
         // System.assert(responseBody.startsWith('!'), 'Le corps de la réponse doit commencer par "!"');
         // System.assert(responseBody.contains('CUST001'), 'Le corps de la réponse doit contenir des données exportées');

     }

      @isTest static void testRESTEndpoint_Error_PublicFinancer() {
         Date testDate = Date.newInstance(2023, 10, 26);
         LegalEntity__c le = [SELECT Id FROM LegalEntity__c WHERE Name='Test Entity WSE'];
         Invoice__c pfNok = [SELECT Id FROM Invoice__c WHERE Name='INV-PF-02']; // Facture qui causera l'erreur

         RestRequest req = new RestRequest();
         RestResponse res = new RestResponse();

         req.requestURI = '/services/apexrest/v1/exportInvoice'; // Adaptez l'URI
         req.httpMethod = 'GET';
         req.params.put('startDate', testDate.format());
         req.params.put('endDate', testDate.format());
         req.params.put('entityId', le.Id);
         req.params.put('excludePrevious', 'false');


         RestContext.request = req;
         RestContext.response = res;

         Test.startTest();
          // Supposons que votre classe REST s'appelle APR01_ExportInvoiceREST
         // APR01_ExportInvoiceREST.doExport(); // Décommentez et adaptez
         Test.stopTest();

         // --- Assertions sur la réponse REST en cas d'erreur gérée ---
         // Si votre endpoint REST attrape l'erreur et retourne un code 400/500 avec un message:
         // System.assertEquals(400, RestContext.response.statusCode, 'Le statut HTTP doit être 400 Bad Request (ou 500)');
         // String responseBody = RestContext.response.responseBody != null ? RestContext.response.responseBody.toString() : '';
         // System.assert(responseBody.contains('ERREUR:'), 'Le corps de la réponse doit contenir le message d\'erreur');
         // System.assert(responseBody.contains('PF NOK'), 'Le corps de la réponse doit mentionner le financeur en erreur');

     }

     // Supprimez cette méthode de test si vous supprimez la méthode i0() de AP14_CommonExportInvoice
     /*
     @isTest static void testAP14_CommonExportInvoice_i0() {
         Test.startTest();
         AP14_CommonExportInvoice.i0(); // Teste la méthode de couverture vide
         Test.stopTest();
         // Aucune assertion nécessaire car la méthode est vide
         System.assert(true, 'Dummy assertion for empty coverage method');
     }
     */

     // Supprimez cette méthode de test si vous supprimez la méthode i0() de APR01_ExportInvoiceREST
      /*
     @isTest static void testAPR01_ExportInvoiceREST_i0() {
         Test.startTest();
         // APR01_ExportInvoiceREST.i0(); // Teste la méthode de couverture vide
         Test.stopTest();
         // Aucune assertion nécessaire car la méthode est vide
         System.assert(true, 'Dummy assertion for empty coverage method');
     }
      */
}
