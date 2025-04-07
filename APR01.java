@isTest
private class APR01_ExportInvoiceREST_TEST {

    private static testMethod void testAPR01_ExportInvoiceREST() {
        Test.startTest();
        
        APR01_ExportInvoiceREST.i0();
        // TODO
        Test.stopTest();
    }
    
    private static testMethod void testAP14_CommonExportInvoice() {
        
        Test.startTest();
        // constructor
        String mode = 'mode'; 
        Date start = date.newinstance(2019, 05, 07);
        Date dEnd = date.newinstance(2019, 05, 07);
        ID entityId = 'a0I58000006pDAM';
        Boolean excludePrevTransfer = true;
    	AP14_CommonExportInvoice testAP = new  AP14_CommonExportInvoice(mode, start, dEnd, entityId, excludePrevTransfer);
          
        AP14_CommonExportInvoice.i0();
        // TODO
        Test.stopTest();
    }
}
