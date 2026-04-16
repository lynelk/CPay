/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  josephtabajjwa
 * Created: 17 Feb 2020
 */

/*Add source_ip_address to the transactions_log table*/
ALTER TABLE merchant_transactions_log ADD COLUMN source_ip_address VARCHAR(255) NOT NULL DEFAULT '';