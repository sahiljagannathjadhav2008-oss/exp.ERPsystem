# Data model

The app uses SQLite locally. Main relationships:

Company → account groups → ledgers → voucher entries → vouchers
Company → items → stock movements → warehouses/batches
Company → sales invoices → sales invoice items
Company → purchase bills → purchase bill items
Company → employees → payroll
Company → bank accounts → bank transactions

A sales invoice posts:

Customer Dr = invoice total
Sales Cr = taxable amount
Output CGST/SGST or IGST Cr = tax

A purchase bill posts:

Purchase Dr = taxable amount
Input CGST/SGST or IGST Dr = tax
Supplier Cr = invoice total

Inventory is represented by stock movements rather than only invoice tables.
