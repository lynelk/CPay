/**
 * Number.prototype.format(n, x)
 *
 * @param integer n: length of decimal
 * @param integer x: length of sections
 */
Number.prototype.format = (n, x) => {
    var re = '\\d(?=(\\d{' + (x || 3) + '})+' + (n > 0 ? '\\.' : '$') + ')';
    return this.toFixed(Math.max(0, ~~n)).replace(new RegExp(re, 'g'), '$&,');
};


let common = {
    base_url: "",
    emailValidation: {
        "validator": (value) => {
            var emailExp = /^[\w\-\.\+]+\@[a-zA-Z0-9\.\-]+\.[a-zA-z0-9]{2,4}$/;
            if (value.match(emailExp)){
                return true;
            } else {
                return false;
                /*return Promise(resolve => {
                    resolve(true);
                });*/
            }
        },
        "message": "Invalid email address."
    },
    phoneValidation: {
        "validator": (value) => {
            var phoneExp = /^\d{1,3}\d{3}\d{6}/;
            if (value.match(phoneExp)){
                return true;
            } else {
                return false;
                /*return Promise(resolve => {
                    resolve(true);
                });*/
            }
        },
        "message": "Invalid phone."
    },
    numericValidation: {
        "validator": (value) => {
            //console.log(value);
            var numericExp = /^\d+(\.\d+)?(\d+)?$/;
            if (value.toString().match(numericExp)){
                return true;
            } else {
                return false;
                /*return Promise(resolve => {
                    resolve(true);
                });*/
            }
        },
        "message": "Invalid number."
    },

    toReduceGridHeight: 142, //The amount to reduce from the windowHeight

    formatDate(date) {
        console.log(date);
        if (date == null || date == "") {
            return "";
        }

        let y = date.getFullYear();
        let m = date.getMonth() + 1;
        if (m < 10) {
            m = "0"+m;
        }
        let d = date.getDate();
        if (d < 10) {
            d = "0"+d;
        }
        return [y,m,d].join('-')
    },

    /**
     * get the date before nofMonths for a given {@param date} 
     * @param {Date} date 
     * @param {Number} nofMonths no of months to get date before
     * @returns {Date} date before nofMonths months
     */
    getDateMonthsBefore(date,nofMonths) {
        var thisMonth = date.getMonth();
        // set the month index of the date by subtracting nofMonths from the current month index
        date.setMonth(thisMonth - nofMonths);
        // When trying to add or subtract months from a Javascript Date() Object which is any end date of a month,  
        // JS automatically advances your Date object to next month's first date if the resulting date does not exist in its month. 
        // For example when you add 1 month to October 31, 2008 , it gives Dec 1, 2008 since November 31, 2008 does not exist.
        // if the result of subtraction is negative and add 6 to the index and check if JS has auto advanced the date, 
        // then set the date again to last day of previous month
        // Else check if the result of subtraction is non negative, subtract nofMonths to the index and check the same.
        if ((thisMonth - nofMonths < 0) && (date.getMonth() != (thisMonth + nofMonths))) {
            date.setDate(0);
        } else if ((thisMonth - nofMonths >= 0) && (date.getMonth() != thisMonth - nofMonths)) {
            date.setDate(0);
        }
        return date;
    },
    
    /**
     * get the date after nofMonths for a given {@param date} 
     * @param {Date} date 
     * @param {Number} nofMonths no of months to get date after
     * @returns {Date} date after nofMonths 
     */
    getDateMonthsAfter(date,nofMonths) {
        var thisMonth = date.getMonth();
        // set the month index of the date by adding nofMonths to the current month index
        date.setMonth(thisMonth + nofMonths);
        // if the result of addition is greater than 11 and subtract nofMonths from the index and check if JS has auto advanced the date, 
        // then set the date again to last day of previous month
        // Else check if the result of addition is not greater than 11, add nofMonths to the index and check the same.
        if ((thisMonth + nofMonths > 11) && (date.getMonth() != (thisMonth - nofMonths))) {
            date.setDate(0);
        } else if ((thisMonth + nofMonths <= 11) && (date.getMonth() != (thisMonth + nofMonths))) {
            date.setDate(0);
        }
        return date;
    },

    getDefaultDateTime() {
        let date = new Date();
        let y = date.getFullYear();
        let m = date.getMonth() + 1;
        if (m < 10) {
            m = "0"+m;
        }
        let d = date.getDate();
        if (d < 10) {
            d = "0"+d;
        }
        return [y,m,d].join('-')+" "+date.getHours()+":"+date.getMinutes()+":"+date.getSeconds();
    },

    encodeHTML: (str) => {
        return str.replace(/[\u00A0-\u9999<>&](?!#)/gim, function(i) {
          return '&#' + i.charCodeAt(0) + ';';
        });
    },
    
    decodeHTML: (str)=>{
        return str.replace(/&#([0-9]{1,3});/gi, function(match, num) {
            return String.fromCharCode(parseInt(num));
        });
    },

    /* System provide */
    privileges: [
        { value: 'CREATE_MERCHANT', text: "CREATE MERCHANT" },
        { value: 'UPDATE_MERCHANT', text: "UPDATE MERCHANT" },
        { value: 'UPDATED_MERCHANT', text: "UPDATED MERCHANT" },
        { value: 'ACCESS_SETTINGS', text: "ACCESS SETTINGS" },
        { value: 'UPDATE_SETTINGS', text: "UPDATE SETTINGS" },
        { value: 'ACCESS_REPORTS', text: "ACCESS REPORTS" },
        { value: 'ACTIVATE_MERCHANT', text: "ACTIVATE MERCHANT" },
        { value: 'CREDIT_MERCHANT', text: "CREDIT MERCHANT" },
        { value: 'DEBIT_MERCHANT', text: "DEBIT MERCHANT" },
        { value: 'CREATE_ADMIN', text: "CREATE ADMIN" },
        { value: 'UPDATE_ADMIN', text: "UPDATE ADMIN" },
        { value: 'DELETE_ADMIN', text: "DELETE ADMIN" },
        { value: 'ACCESS_ADMIN', text: "ACCESS ADMIN" },
        { value: 'ACCESS_TRANSACTION_LOG', text: "ACCESS TRANSACTIONS LOG" },
        { value: 'ACCESS_AUDITTRAIL', text: "ACCESS AUDIT TRAIL"},
        { value: 'RESOLVE_TRANSACTIONS', text: "RESOLVE TRANSACTIONS"}
    ],
    /* System provide */
    merchant_privileges: [
        { value: 'CREATE_BATCH_TX', text: "CREATE BATCH TRANSACTION" },
        { value: 'APPROVE_BATCH_TX', text: "APPROVE BATCH TRANSACTION" },
        { value: 'DOWNLOAD_REPORTS', text: "DOWNLOAD REPORTS" },
        { value: 'ACCESS_SETTINGS', text: "ACCESS SETTINGS" },
        { value: 'UPDATE_SETTINGS', text: "UPDATE SETTINGS" },
        { value: 'ACCESS_TRANSACTION_LOG', text: "ACCESS TRANSACTIONS LOG" },
        { value: 'ACCESS_AUDITTRAIL', text: "ACCESS AUDIT TRAIL"},
        { value: 'CREATE_ADMIN', text: "CREATE ADMIN" },
        { value: 'UPDATE_ADMIN', text: "UPDATE ADMIN" },
        { value: 'DELETE_ADMIN', text: "DELETE ADMIN" },
        { value: 'ACCESS_ADMIN', text: "ACCESS ADMIN" },
        {value: 'ACCESS_SMS_LOG', text: "ACCESS SMS LOG"},
        {value: 'SEND_SMS', text: "SEND SMS"},
        {value: 'BUY_SMS', text: "BUY SMS"},
        
    ],
    tx_types: [
        { value: 'FLOAT CREDIT', text: "FLOAT CREDIT" },
        { value: 'FLOAT DEBIT', text: "FLOAT DEBIT" },
        { value: 'FLOAT STOCK CREDIT', text: "FLOAT STOCK CREDIT" },
        { value: 'FLOAT STOCK DEBIT', text: "FLOAT STOCK DEBIT" },
        { value: 'FLOAT STOCK CREDIT REVERSAL', text: "FLOAT STOCK CREDIT REVERSAL" },
        { value: 'FLOAT STOCK DEBIT REVERSAL', text: "FLOAT STOCK DEBIT REVERSAL" },
        { value: 'SMS PURCHASE', text: "SMS PURCHASE" },
    ],
    balance_type: [
        { value: 'mtnmm_balance', text: "UGX MTN MM" },
        { value: 'airtelmm_balance', text: "UGX AIRTEL MM" },
        { value: 'sms_balance', text: "UGX SMS" },
        { value: 'safaricom_balance', text: "KES MPESAMM" },
    ],
    account_types: [
        { value: 'phone', text: "PHONE" },
        /*{ value: 'cpay', text: "CPAY" },*/
    ],
    allowed_apis: [
        { value: 'MOBILE_MONEY_PAYIN', text: "MOBILE MONEY PAYIN" },
        { value: 'MOBILE_MONEY_PAYOUT', text: "MOBILE MONEY PAYOUT" },
        { value: 'MULTIPLE_PAYOUT', text: "MULTIPLE PAYOUT" },
        { value: 'MULTIPLE_CHECKSTATUS', text: "MULTIPLE CHECKSTATUS" },
        { value: 'TRANSACTION_CHECKSTATUS', text: "TRANSACTION CHECKSTATUS" },
        { value: 'BALANCE_CHECK', text: "BALANCE CHECK" },
        { value: 'ACCOUNT_VALIDATION', text: "ACCOUNT VALIDATION" },
        { value: 'API_SEND_SMS', text: "SEND SMS" },
    ],
    formatNumber(num) {
        return num.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,')
    }
};

module.exports = common;