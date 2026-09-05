import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

class AppDb {
  AppDb._();
  static final AppDb instance = AppDb._();
  Database? _db;

  Future<Database> get db async {
    if (_db != null) return _db!;
    final dir = await getApplicationDocumentsDirectory();
    final path = p.join(dir.path, 'offline_erp.sqlite');
    _db = await openDatabase(path, version: 1, onConfigure: (db) async {
      await db.execute('PRAGMA foreign_keys = ON');
      await db.execute('PRAGMA journal_mode = WAL');
    }, onCreate: _create);
    return _db!;
  }

  String id() => DateTime.now().microsecondsSinceEpoch.toString();

  Future<void> _create(Database db, int version) async {
    final sql = <String>[
      '''CREATE TABLE companies(id TEXT PRIMARY KEY, name TEXT NOT NULL, legal_name TEXT, gstin TEXT, pan TEXT, phone TEXT, email TEXT, address TEXT, city TEXT, state TEXT, state_code TEXT, pincode TEXT, financial_year_start TEXT, currency TEXT NOT NULL DEFAULT 'INR', created_at TEXT NOT NULL)''',
      '''CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT)''',
      '''CREATE TABLE account_groups(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, parent_id TEXT, name TEXT NOT NULL, nature TEXT NOT NULL, is_system INTEGER NOT NULL DEFAULT 0, UNIQUE(company_id,name), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE ledgers(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, group_id TEXT, code TEXT, name TEXT NOT NULL, opening_balance REAL NOT NULL DEFAULT 0, opening_type TEXT, gstin TEXT, pan TEXT, address TEXT, phone TEXT, email TEXT, credit_limit REAL DEFAULT 0, credit_days INTEGER DEFAULT 0, is_customer INTEGER DEFAULT 0, is_supplier INTEGER DEFAULT 0, is_bank INTEGER DEFAULT 0, is_cash INTEGER DEFAULT 0, is_active INTEGER DEFAULT 1, FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE, FOREIGN KEY(group_id) REFERENCES account_groups(id))''',
      '''CREATE TABLE voucher_types(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, prefix TEXT NOT NULL, next_no INTEGER NOT NULL DEFAULT 1, UNIQUE(company_id,code), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE vouchers(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, voucher_type_id TEXT NOT NULL, voucher_no TEXT NOT NULL, voucher_date TEXT NOT NULL, reference_no TEXT, narration TEXT, status TEXT NOT NULL DEFAULT 'POSTED', created_at TEXT NOT NULL, FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE, FOREIGN KEY(voucher_type_id) REFERENCES voucher_types(id))''',
      '''CREATE TABLE voucher_entries(id TEXT PRIMARY KEY, voucher_id TEXT NOT NULL, ledger_id TEXT NOT NULL, debit REAL NOT NULL DEFAULT 0, credit REAL NOT NULL DEFAULT 0, narration TEXT, FOREIGN KEY(voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE, FOREIGN KEY(ledger_id) REFERENCES ledgers(id))''',
      '''CREATE TABLE units(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, name TEXT NOT NULL, symbol TEXT NOT NULL, decimals INTEGER NOT NULL DEFAULT 2, UNIQUE(company_id,symbol), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE item_categories(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, parent_id TEXT, name TEXT NOT NULL, UNIQUE(company_id,name), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE stock_items(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, category_id TEXT, unit_id TEXT, sku TEXT, name TEXT NOT NULL, hsn_code TEXT, sac_code TEXT, gst_rate REAL NOT NULL DEFAULT 0, opening_qty REAL NOT NULL DEFAULT 0, opening_rate REAL NOT NULL DEFAULT 0, reorder_level REAL DEFAULT 0, batch_enabled INTEGER DEFAULT 0, serial_enabled INTEGER DEFAULT 0, purchase_ledger_id TEXT, sales_ledger_id TEXT, is_active INTEGER DEFAULT 1, UNIQUE(company_id,sku), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE, FOREIGN KEY(category_id) REFERENCES item_categories(id), FOREIGN KEY(unit_id) REFERENCES units(id))''',
      '''CREATE TABLE warehouses(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, parent_id TEXT, name TEXT NOT NULL, address TEXT, UNIQUE(company_id,name), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE stock_batches(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, stock_item_id TEXT NOT NULL, warehouse_id TEXT NOT NULL, batch_no TEXT, manufacturing_date TEXT, expiry_date TEXT, quantity REAL NOT NULL DEFAULT 0, rate REAL NOT NULL DEFAULT 0, FOREIGN KEY(stock_item_id) REFERENCES stock_items(id), FOREIGN KEY(warehouse_id) REFERENCES warehouses(id))''',
      '''CREATE TABLE stock_movements(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, stock_item_id TEXT NOT NULL, warehouse_id TEXT NOT NULL, batch_id TEXT, movement_date TEXT NOT NULL, reference_type TEXT, reference_id TEXT, quantity_in REAL NOT NULL DEFAULT 0, quantity_out REAL NOT NULL DEFAULT 0, rate REAL NOT NULL DEFAULT 0, FOREIGN KEY(stock_item_id) REFERENCES stock_items(id), FOREIGN KEY(warehouse_id) REFERENCES warehouses(id))''',
      '''CREATE TABLE sales_invoices(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, voucher_id TEXT, customer_id TEXT NOT NULL, invoice_no TEXT NOT NULL, invoice_date TEXT NOT NULL, due_date TEXT, place_of_supply TEXT, subtotal REAL NOT NULL DEFAULT 0, discount_amount REAL NOT NULL DEFAULT 0, taxable_amount REAL NOT NULL DEFAULT 0, cgst_amount REAL NOT NULL DEFAULT 0, sgst_amount REAL NOT NULL DEFAULT 0, igst_amount REAL NOT NULL DEFAULT 0, cess_amount REAL NOT NULL DEFAULT 0, round_off REAL NOT NULL DEFAULT 0, grand_total REAL NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'POSTED', UNIQUE(company_id,invoice_no), FOREIGN KEY(customer_id) REFERENCES ledgers(id), FOREIGN KEY(voucher_id) REFERENCES vouchers(id))''',
      '''CREATE TABLE sales_invoice_items(id TEXT PRIMARY KEY, invoice_id TEXT NOT NULL, stock_item_id TEXT, warehouse_id TEXT, batch_id TEXT, description TEXT, quantity REAL NOT NULL, rate REAL NOT NULL, discount_percent REAL NOT NULL DEFAULT 0, discount_amount REAL NOT NULL DEFAULT 0, taxable_amount REAL NOT NULL DEFAULT 0, gst_rate REAL NOT NULL DEFAULT 0, cgst_amount REAL NOT NULL DEFAULT 0, sgst_amount REAL NOT NULL DEFAULT 0, igst_amount REAL NOT NULL DEFAULT 0, total_amount REAL NOT NULL, FOREIGN KEY(invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE, FOREIGN KEY(stock_item_id) REFERENCES stock_items(id))''',
      '''CREATE TABLE purchase_bills(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, voucher_id TEXT, supplier_id TEXT NOT NULL, bill_no TEXT NOT NULL, bill_date TEXT NOT NULL, due_date TEXT, subtotal REAL NOT NULL DEFAULT 0, discount_amount REAL NOT NULL DEFAULT 0, taxable_amount REAL NOT NULL DEFAULT 0, cgst_amount REAL NOT NULL DEFAULT 0, sgst_amount REAL NOT NULL DEFAULT 0, igst_amount REAL NOT NULL DEFAULT 0, cess_amount REAL NOT NULL DEFAULT 0, round_off REAL NOT NULL DEFAULT 0, grand_total REAL NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'POSTED', UNIQUE(company_id,bill_no), FOREIGN KEY(supplier_id) REFERENCES ledgers(id), FOREIGN KEY(voucher_id) REFERENCES vouchers(id))''',
      '''CREATE TABLE purchase_bill_items(id TEXT PRIMARY KEY, purchase_bill_id TEXT NOT NULL, stock_item_id TEXT, warehouse_id TEXT, batch_id TEXT, quantity REAL NOT NULL, rate REAL NOT NULL, discount_amount REAL NOT NULL DEFAULT 0, taxable_amount REAL NOT NULL DEFAULT 0, gst_rate REAL NOT NULL DEFAULT 0, cgst_amount REAL NOT NULL DEFAULT 0, sgst_amount REAL NOT NULL DEFAULT 0, igst_amount REAL NOT NULL DEFAULT 0, total_amount REAL NOT NULL, FOREIGN KEY(purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE, FOREIGN KEY(stock_item_id) REFERENCES stock_items(id))''',
      '''CREATE TABLE tax_rates(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, name TEXT NOT NULL, gst_rate REAL NOT NULL, cgst_rate REAL NOT NULL DEFAULT 0, sgst_rate REAL NOT NULL DEFAULT 0, igst_rate REAL NOT NULL DEFAULT 0, cess_rate REAL NOT NULL DEFAULT 0, UNIQUE(company_id,name), FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE hsn_codes(id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, description TEXT, gst_rate REAL)''',
      '''CREATE TABLE bank_accounts(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, ledger_id TEXT NOT NULL, bank_name TEXT, account_number TEXT, ifsc_code TEXT, branch_name TEXT, opening_balance REAL DEFAULT 0, FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE, FOREIGN KEY(ledger_id) REFERENCES ledgers(id))''',
      '''CREATE TABLE bank_transactions(id TEXT PRIMARY KEY, bank_account_id TEXT NOT NULL, transaction_date TEXT NOT NULL, reference_no TEXT, description TEXT, debit REAL DEFAULT 0, credit REAL DEFAULT 0, bank_balance REAL, is_reconciled INTEGER DEFAULT 0, reconciled_at TEXT, FOREIGN KEY(bank_account_id) REFERENCES bank_accounts(id) ON DELETE CASCADE)''',
      '''CREATE TABLE employees(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, employee_code TEXT, name TEXT NOT NULL, joining_date TEXT, phone TEXT, email TEXT, department TEXT, designation TEXT, basic_salary REAL DEFAULT 0, is_active INTEGER DEFAULT 1, FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE)''',
      '''CREATE TABLE payroll(id TEXT PRIMARY KEY, company_id TEXT NOT NULL, employee_id TEXT NOT NULL, salary_month TEXT NOT NULL, basic_salary REAL DEFAULT 0, allowances REAL DEFAULT 0, deductions REAL DEFAULT 0, net_salary REAL DEFAULT 0, status TEXT DEFAULT 'PROCESSED', UNIQUE(employee_id,salary_month), FOREIGN KEY(employee_id) REFERENCES employees(id))''',
      '''CREATE TABLE audit_logs(id TEXT PRIMARY KEY, company_id TEXT, table_name TEXT, record_id TEXT, action TEXT, old_data TEXT, new_data TEXT, created_at TEXT NOT NULL, FOREIGN KEY(company_id) REFERENCES companies(id))''',
    ];
    for (final s in sql) {
      await db.execute(s);
    }
  }

  Future<String?> companyId() async {
    final d = await db;
    final rows = await d.query('companies', columns: ['id'], limit: 1);
    return rows.isEmpty ? null : rows.first['id'] as String;
  }

  Future<String> ensureCompany() async {
    final d = await db;
    final existing = await companyId();
    if (existing != null) return existing;
    final cid = id();
    await d.insert('companies', {
      'id': cid,
      'name': 'My Business',
      'legal_name': 'My Business',
      'currency': 'INR',
      'financial_year_start': '${DateTime.now().year}-04-01',
      'created_at': DateTime.now().toIso8601String(),
    });
    await seed(cid);
    return cid;
  }

  Future<void> seed(String cid) async {
    final d = await db;
    final groups = <Map<String, Object?>>[
      {'name':'Capital Account','nature':'EQUITY','is_system':1},
      {'name':'Current Assets','nature':'ASSET','is_system':1},
      {'name':'Fixed Assets','nature':'ASSET','is_system':1},
      {'name':'Current Liabilities','nature':'LIABILITY','is_system':1},
      {'name':'Loans (Liability)','nature':'LIABILITY','is_system':1},
      {'name':'Sales Accounts','nature':'INCOME','is_system':1},
      {'name':'Purchase Accounts','nature':'EXPENSE','is_system':1},
      {'name':'Direct Expenses','nature':'EXPENSE','is_system':1},
      {'name':'Indirect Expenses','nature':'EXPENSE','is_system':1},
      {'name':'Indirect Income','nature':'INCOME','is_system':1},
    ];
    final gid = <String,String>{};
    for (final g in groups) {
      final idv = id(); gid[g['name'] as String] = idv;
      await d.insert('account_groups', {'id':idv,'company_id':cid,...g});
    }
    Future<void> ledger(String name, String group, {double opening=0, String type='Dr', bool customer=false, bool supplier=false, bool cash=false, bool bank=false}) async {
      await d.insert('ledgers', {'id':id(),'company_id':cid,'group_id':gid[group],'name':name,'opening_balance':opening,'opening_type':type,'is_customer':customer?1:0,'is_supplier':supplier?1:0,'is_cash':cash?1:0,'is_bank':bank?1:0});
    }
    await ledger('Cash','Current Assets',cash:true);
    await ledger('Bank','Current Assets',bank:true);
    await ledger('Sales','Sales Accounts',type:'Cr');
    await ledger('Purchase','Purchase Accounts');
    await ledger('Input CGST','Current Assets');
    await ledger('Input SGST','Current Assets');
    await ledger('Input IGST','Current Assets');
    await ledger('Output CGST','Current Liabilities',type:'Cr');
    await ledger('Output SGST','Current Liabilities',type:'Cr');
    await ledger('Output IGST','Current Liabilities',type:'Cr');
    await ledger('Round Off','Indirect Expenses');
    await ledger('General Expenses','Indirect Expenses');
    await ledger('Capital Account','Capital Account',type:'Cr');
    final unitId=id(); await d.insert('units',{'id':unitId,'company_id':cid,'name':'Pieces','symbol':'Nos','decimals':2});
    final kgId=id(); await d.insert('units',{'id':kgId,'company_id':cid,'name':'Kilogram','symbol':'Kg','decimals':3});
    await d.insert('warehouses',{'id':id(),'company_id':cid,'name':'Main Warehouse'});
    for (final v in [['Sales Invoice','SI','SI-',1],['Purchase Bill','PB','PB-',1],['Receipt','RC','RC-',1],['Payment','PM','PM-',1],['Journal','JV','JV-',1],['Contra','CN','CN-',1],['Credit Note','CNt','CNt-',1],['Debit Note','DN','DN-',1]]) {
      await d.insert('voucher_types',{'id':id(),'company_id':cid,'name':v[0],'code':v[1],'prefix':v[2],'next_no':v[3]});
    }
    for (final r in [5,12,18,28]) {
      await d.insert('tax_rates',{'id':id(),'company_id':cid,'name':'GST $r%','gst_rate':r,'cgst_rate':r/2,'sgst_rate':r/2,'igst_rate':r});
    }
    await d.insert('settings',{'key':'company_id','value':cid});
    await d.insert('settings',{'key':'theme','value':'system'});
  }

  Future<List<Map<String,Object?>>> query(String table,{String? where,List<Object?>? args,String? orderBy,int? limit}) async {
    final d=await db; return d.query(table,where:where,whereArgs:args,orderBy:orderBy,limit:limit);
  }

  Future<int> insert(String table, Map<String,Object?> values) async {
    final d=await db; return d.insert(table,values);
  }

  Future<int> update(String table, Map<String,Object?> values, String where, List<Object?> args) async {
    final d=await db; return d.update(table,values,where:where,whereArgs:args);
  }

  Future<int> delete(String table, String where, List<Object?> args) async {
    final d=await db; return d.delete(table,where:where,whereArgs:args);
  }

  Future<T> transaction<T>(Future<T> Function(Transaction txn) action) async {
    final d=await db; return d.transaction(action);
  }

  Future<String> nextVoucherNo(String cid, String code) async {
    final d=await db;
    final rows=await d.query('voucher_types',where:'company_id=? AND code=?',whereArgs:[cid,code],limit:1);
    if(rows.isEmpty) throw Exception('Voucher type not configured: $code');
    final r=rows.first; final no=r['next_no'] as int; final prefix=r['prefix'] as String;
    await d.update('voucher_types',{'next_no':no+1},where:'id=?',whereArgs:[r['id']]);
    return '$prefix$no';
  }

  Future<void> audit(String cid,String table,String record,String action,{Map<String,Object?>? oldData,Map<String,Object?>? newData}) async {
    await insert('audit_logs',{'id':id(),'company_id':cid,'table_name':table,'record_id':record,'action':action,'old_data':oldData==null?null:jsonEncode(oldData),'new_data':newData==null?null:jsonEncode(newData),'created_at':DateTime.now().toIso8601String()});
  }

  Future<Map<String,Object?>> summary(String cid) async {
    final d=await db;
    Future<double> sum(String table,String col) async { final r=await d.rawQuery('SELECT COALESCE(SUM($col),0) v FROM $table WHERE company_id=?',[cid]); return (r.first['v'] as num).toDouble(); }
    final sales=await sum('sales_invoices','grand_total');
    final purchase=await sum('purchase_bills','grand_total');
    final stock=await d.rawQuery('SELECT COALESCE(SUM(quantity_in-quantity_out),0) q FROM stock_movements WHERE company_id=?',[cid]);
    final customers=await d.rawQuery('SELECT COUNT(*) c FROM ledgers WHERE company_id=? AND is_customer=1',[cid]);
    final suppliers=await d.rawQuery('SELECT COUNT(*) c FROM ledgers WHERE company_id=? AND is_supplier=1',[cid]);
    return {'sales':sales,'purchase':purchase,'stock':(stock.first['q'] as num).toDouble(),'customers':customers.first['c'],'suppliers':suppliers.first['c']};
  }

  Future<List<String>> tables() async => ['companies','settings','account_groups','ledgers','voucher_types','vouchers','voucher_entries','units','item_categories','stock_items','warehouses','stock_batches','stock_movements','sales_invoices','sales_invoice_items','purchase_bills','purchase_bill_items','tax_rates','hsn_codes','bank_accounts','bank_transactions','employees','payroll','audit_logs'];

  Future<String> exportJson() async {
    final d=await db; final data=<String,dynamic>{'schema_version':1,'exported_at':DateTime.now().toIso8601String(),'tables':{}};
    for(final t in await tables()) data['tables'][t]=await d.query(t);
    return const JsonEncoder.withIndent('  ').convert(data);
  }

  Future<void> importJson(String json) async {
    final decoded=jsonDecode(json) as Map<String,dynamic>; final all=decoded['tables'] as Map<String,dynamic>;
    final d=await db;
    await d.transaction((txn) async {
      final order=await tables();
      for(final t in order.reversed) await txn.delete(t);
      for(final t in order){
        final rows=(all[t] as List<dynamic>? ?? []).cast<Map<String,dynamic>>();
        for(final row in rows) await txn.insert(t,row,conflictAlgorithm:ConflictAlgorithm.replace);
      }
    });
  }

  Future<File> databaseFile() async {
    final dir=await getApplicationDocumentsDirectory(); return File(p.join(dir.path,'offline_erp.sqlite'));
  }
}
