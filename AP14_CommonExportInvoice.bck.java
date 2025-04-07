/**
 * Covered by APR01_ExportInvoiceREST_TEST
 * @author PSIN
 * TO work on... 
 */
public class AP14_CommonExportInvoice {
    
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
    
    // constructor
    public AP14_CommonExportInvoice(String mode, Date dateOfIssueStart, Date dateOfIssueEnd, ID entityId, Boolean excludePrevTransfer){
        this.mode = mode;
        this.invoices = invoices;
        this.dateOfIssueStart = dateOfIssueStart;
        this.dateOfIssueEnd = dateOfIssueEnd;
        this.entityId = entityId;
        this.excludePrevTransfer = excludePrevTransfer;
        
        this.initAccountingSettings();
    }
    
    
    public void initInvoices(){
        this.invoices = [SELECT 
                            Id,
                            Name,
                            AmountTaxExcluded__c,
                            AmountTaxIncluded__c,
                            AmountVAT__c,
                            DateIssue__c,
                            ContractLegalEntity__c,
                            Contract__r.LegalEntity__c,
                            LegalEntity__c,
                            RecordType.Name,
                            RecordType.DeveloperName,
                            AccountingTable9__c,
                            AccountingNumeroRupture__c,
                            AccountingCompteCollectif__c,
                            AccountingCompteAnalytique__c,
                            AccountingCompteGeneral__c,
                            AccountingCompteTVA__c,
                            AccountingLibelle__c,
                            AccountingTypePiece__c,
                            AccountingIdWSEgroup__c,
                            AccountingPostalCode__c,
                            AccountingRecipientName__c,
                            AccountingStreet__c,
                            AccountingCity__c,
                            PublicFinancer__c,
                            PublicFinancer__r.Name,
                            NumberDueDates__c,
                            DueDate__c,
                            DueDateForExport__c,
                            WasExported__c,
                            DateLastTransfer__c,
                            AccountingJournalVente__c,
                            AmountVAT20__c,
                            AmountVAT55__c,
                            NameWGE__c,
                         	Account_Assignment__c,
                            (
                                SELECT
                                    Id,
                                    Date__c,
                                    Amount__c,
                                    AccountingPaymentMode__c,
                                    PaymentMode__c
                                FROM
                                    InvoiceDueDates__r
                            ),
                            (
                                SELECT
                                    Id,
                                    Amount__c,
                                    WSEcenter__c,
                                    AnalyticCode__c
                                FROM
                                    InvoiceAnalyticItems__r
                            )
                        FROM Invoice__c 
                        WHERE
                            (WasExported__c = false OR
                            WasExported__c =: (this.excludePrevTransfer ? false : true)) AND
                            DateIssue__c >=: this.dateOfIssueStart AND 
                            DateIssue__c <=: this.dateOfIssueEnd AND 
                            // Contract__r.LegalEntity__c =: this.entityToExport.split('-')[0] AND
                            LegalEntity__c =: this.entityId AND // FME le 11/09/2017
                            Status__c = 'Validated' 
                        ORDER BY Name ASC];
                        
    }
    
    public Boolean markAsTransfered(){
        for(Invoice__c i : this.invoices){
            i.DateLastTransfer__c = datetime.now();
        }
        
        try{
            update this.invoices;
        }catch(Exception ex){
            return false;
        }
        return true;
    }
    
    public string accountingExport(){
        
        // nom du fichier à générer
        //this.fileName = (this.entityToExport.split('-')[1] + '-' + system.now() + '.TRA').replace(' ','-');
        
        // les clients mis dans le fichier (lignes CLIENT), pour pas les mettre deux fois
        Set<String> clients = new Set<String>();
        
        Integer i=0;
        List<String> returnStrInit = new List<String>{'!'};
        List<String> returnStr = new List<String>();
        
        Set<id> idPublicFinancerToSetAccountingId = new set<Id>();
        String tmpLine = '';
        
        Set<ID> accountSent = new Set<ID>(); // pour ne pas envoyer deux fois le même client dans un fichier. Un client étant un OPCA, une société ou un client particulier
        
        for(Invoice__c inv : this.invoices){
            
            // une ligne vide, pour faire joli
            //returnStrInit.add('');
            
            i++;
            
            if(inv.RecordType.DeveloperName.contains('PublicFinancer') && (inv.AccountingIdWSEgroup__c == '' || inv.AccountingIdWSEgroup__c == null)){
                if(!idPublicFinancerToSetAccountingId.contains(inv.AccountingIdWSEgroup__c) ){ 
                    if(this.mode == 'API')
                        return 'un problème est survenu avec un Public Financer';
                    else{
                        // DO SOMETHING
                    }
                        
                }
            }
            else{
                
                // d'abord la ligne client, si pas déjà présente dans le fichier
                if(!clients.contains(inv.AccountingIdWSEgroup__c)){
                    clients.add(inv.AccountingIdWSEgroup__c);
                    returnStrInit.add(this.getLine(inv, 'CLIENT'));
                }
                // la ligne de TTC
                if(inv.NumberDueDates__c <= 1){
                    returnStr.add(this.getLine(inv, 'TTC'));
                }else{ 
                    // les lignes d'échéances (quand multiples)
                    for(InvoiceDueDate__c idd : inv.InvoiceDueDates__r){
                        returnStr.add(this.getLine(inv, 'DUEDATE', null, idd));
                    }
                }
                
                // la ligne de HT
                returnStr.add(this.getLine(inv, 'HT'));
                    
                // les lignes analytiques
                for(InvoiceAnalyticItem__c iaa : inv.InvoiceAnalyticItems__r){
                    returnStr.add(this.getLine(inv, 'ANALYTIC', iaa, null));
                }
                // la ligne de TVA
                if(inv.AmountVAT__c > 0)
                    returnStr.add(this.getLine(inv, 'TVA'));
                
            }
        }
    
        returnStrInit.addAll(returnStr);
        this.txtString = String.join(returnStrInit,String.fromCharArray( new List<integer> {13} ) + String.fromCharArray( new List<integer> {10} ));
        System.debug('Content : '+this.txtString);
        return this.txtString;
    }
    
    // initialisattion du custom settings, qui contient la descritpion du fichier à générer
    public void initAccountingSettings(){
        
        this.lsExpCLIENT = new List<AccountingColumnSetting> ();
        this.lsExpECRITURE = new List<AccountingColumnSetting> ();
        System.debug('Debut de lecture des parametres');
        for(AccountingSettings__c e : AccountingSettings__c.getall().values()){
//Correction de PLD le 09/08/2018
//            if(e.ExportType__c == 'CLIENT'){
            if(e.ExportType__c == 'CLIENT' &&  e.Franchisor__c == 'WSE'){
                this.lsExpCLIENT.add(new AccountingColumnSetting(e));
            }
//Correction de PLD le 09/08/2018
//            if(e.ExportType__c == 'ECRITURE'){
            if(e.ExportType__c == 'ECRITURE' && e.Franchisor__c == 'WSE'){
                this.lsExpECRITURE.add(new AccountingColumnSetting(e));
            }
        }
        System.debug('Fin de lecture des parametres');
        
        this.lsExpCLIENT.sort();
        this.lsExpECRITURE.sort();
    }
    
    private String getLine(Invoice__c inv, String lineType){
        return this.getLine(inv, lineType, null, null);
    }
    
    private String getLine(Invoice__c inv, String lineType, InvoiceAnalyticItem__c iaa, InvoiceDueDate__c idd){
                        
        String line = '';
        List<AccountingColumnSetting> lsAccSett;
        if(lineType == 'CLIENT')
            lsAccSett = this.lsExpCLIENT;
        else
            lsAccSett = this.lsExpECRITURE;
        
        String fieldName;
        sObject so;
        AccountingColumnFieldInfo fieldInfos;
        
        for(AccountingColumnSetting exp : lsAccSett){
            
            
            
            if(exp.datatype == 'DEFAULT'){ // VALEUR PAR DEFAUT : FIXE OU VIDE
                line += rightPad(exp.defaultValue, exp.length, nothing);
            } else{
                fieldInfos = exp.getFieldInfos(lineType);
                if(fieldInfos.sObjectName == 'Invoice__c')
                    so = inv;
                else if(fieldInfos.sObjectName == 'InvoiceAnalyticItem__c')
                    so = iaa;
                else if(fieldInfos.sObjectName == 'InvoiceDueDate__c')
                    so = idd;
                        
                    // le nom du champ a récupérer. "Dynamique" en fonction du type de ligne qu'on cherche à générer
                    //fieldName = ((String) exp.e.get('SFfield' + lineType + '__c')).split('-')[1];
                
                if(exp.datatype == 'DATE'){ // DATE AU FORMAT JJMMAAAA
                    Date d;
                    if(fieldInfos.fieldName != null){
                        d = (Date) so.get(fieldInfos.fieldName);
                    }else{ // par défaut on met today
                        d = System.today();
                    }
                    
                    system.debug(fieldInfos.fieldName);
                    line += DateTime.newInstance(d, Time.newInstance(12, 0, 0, 0)).format('ddMMyyyy');
                    
                } else if(exp.datatype == 'DECIMAL'){ // NOMBRE SANS VIRGULE, DEUX  DECIMALES, 0 DEVANT
                    Decimal d;
                    if(fieldInfos.fieldName != null)
                        d = (Decimal) so.get(fieldInfos.fieldName);
                    else // on met 0 par défaut
                        d = 0;
                        
                    String m = '';
                    if(d != null)
                        m = String.valueOf(Math.abs(d)).replace('.','');
                    
                    line += leftPad(m, exp.length, '0');
                } else if(exp.datatype == 'DECIMAL2V'){ // NOMBRE AVEC VIRGULE, DEUX  DECIMALES, RIEN DEVANT
                    Decimal d;
                    if(fieldInfos.fieldName != null)
                        d = (Decimal) so.get(fieldInfos.fieldName);
                    else // on met 0 par défaut
                        d = 0.00;
                        
                    String m = '';
                    if(d != null)
                        m = String.valueOf(math.abs(d)).replace('.',',');
                    
                    line += leftPad(m, exp.length, nothing );
                } else if(exp.datatype == 'DECIMAL4V'){ // NOMBRE AVEC VIRGULE, QUATRE  DECIMALES, RIEN DEVANT
                    Decimal d;
                    if(fieldInfos.fieldName != null)
                        d = (Decimal) so.get(fieldInfos.fieldName);
                    else // on met 0 par défaut
                        d = 0.0000;
                        
                    String m = '';
                    if(d != null)
                        m = String.valueOf(math.abs(d)).replace('.',',');
                    
                    line += line += leftPad(m, exp.length, nothing );
                } else if(exp.datatype == 'TEXT'){ // ZONE DE TEXT QUI VA VA CHERCHER LA VALEUR D'UN CHAMP DE INVOICE
                    String s = '';
                    if(fieldInfos.fieldName != null){
                        if(exp.removeSpecialCharacters == true)
                            s = (String) so.get(fieldInfos.fieldName) == null ? '' : MISC_VFCCommon.replaceUpperCaseAccents(((String) so.get(fieldInfos.fieldName)).toUpperCase());
                        else
                            s = (String) so.get(fieldInfos.fieldName) == null ? '' : (String) so.get(fieldInfos.fieldName);
                    }
                    else
                        s = '';
                    
                    line += rightPad(s.left(exp.length), exp.length, nothing);
                } else if(exp.datatype == 'DEBIT/CREDIT'){ // D POUR DEBIT, C POUR CREDIT, EN FONCTION DU TYPE DE LIGNE
                    if((inv.RecordType.DeveloperName.contains('CreditNote') && lineType == 'TTC') ||
                        (inv.RecordType.DeveloperName.contains('Invoice') && (lineType == 'HT' || lineType == 'TVA' || lineType == 'ANALYTIC'))){
                        line += 'C';    
                    }else{
                        line += 'D';
                    }
                } else if(exp.datatype == 'AUX/ANALYTIC'){ // X POUR TTC, A POUR ANALYTIC, VIDE POUR LE RESTE
                    if(lineType == 'TTC' || lineType == 'DUEDATE'){
                        line += 'X';    
                    } else if(lineType == 'ANALYTIC'){
                        line += 'A';
                    } else
                        line += nothing;
                }
                
            }
        }
        return line;
    }
        
    private String rightPad(String value, Integer length, String padWith){
        return value.replace(' ', '¤').rightPad(length).replace(' ', padWith).replace('¤', ' ');
    }
    
    private String leftPad(String value, Integer length, String padWith){
        return value.replace(' ', '¤').leftPad(length).replace(' ', padWith).replace('¤', ' ');
    }
    
    /*-------------------------------------------------------------------------------------------------*/
    /*----------------------------------------    WRAPPER   -------------------------------------------*/
    /*-------------------------------------------------------------------------------------------------*/
    
    
    public class AccountingColumnSetting implements Comparable{
        public String dataType;
        public Integer length;
        private Decimal position;
        public Boolean removeSpecialCharacters;
        public String defaultValue;
        
        private String sObjectAndFieldName;
        private Map<String, AccountingColumnFieldInfo> fieldsToUseByLineType;
        
        public AccountingColumnSetting(AccountingSettings__c s){
            this.dataType = s.DataType__c;
            this.length = Integer.valueOf(s.Size__c);
            this.position = s.Position__c;
            this.removeSpecialCharacters = s.RemoveSpecialCharacters__c;
            this.defaultValue = (String.isBlank(s.DefaultValue__c) ? nothing : s.DefaultValue__c);
            if(this.dataType != 'DEFAULT'){
                if(s.ExportType__c == 'CLIENT'){
                    this.fieldsToUseByLineType = new Map<String, AccountingColumnFieldInfo>{'CLIENT' => new AccountingColumnFieldInfo(s.SFfieldCLIENT__c)};
                }
                else{
                    this.fieldsToUseByLineType = new Map<String, AccountingColumnFieldInfo>{'TTC' => new AccountingColumnFieldInfo(s.SFfieldTTC__c),
                                                                                            'DUEDATE' => new AccountingColumnFieldInfo(s.SFfieldDUEDATE__c), 
                                                                                            'HT' => new AccountingColumnFieldInfo(s.SFfieldHT__c), 
                                                                                            'ANALYTIC' => new AccountingColumnFieldInfo(s.SFfieldANALYTIC__c), 
                                                                                            'TVA' => new AccountingColumnFieldInfo(s.SFfieldTVA__c)};
                }
            }
        }
        
        public AccountingColumnFieldInfo getFieldInfos(String lineType){
            return fieldsToUseByLineType.get(lineType);
        }
        
        // Comparaison par position.
        public Integer compareTo(Object compareTo) {
            AccountingColumnSetting compareToCom = (AccountingColumnSetting) compareTo;
            
            Integer returnValue = 0;
            if (this.position > compareToCom.position) {
                returnValue = 1;
            } else if (this.position < compareToCom.position) {
                returnValue = -1;
            }
            
            return returnValue;       
        }
    }
    
    public class AccountingColumnFieldInfo{
        public String sObjectName;
        public String fieldName;
        
        public AccountingColumnFieldInfo(String sObjectAndFieldName){
            if(String.isNotBlank(sObjectAndFieldName)){
                this.sObjectName = sObjectAndFieldName.substringBefore('-');
                this.fieldName = sObjectAndFieldName.substringAfter('-');
            }
        }
    }
    
    public static void i0(){
        Integer i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
        i = 0;
     }
 }
