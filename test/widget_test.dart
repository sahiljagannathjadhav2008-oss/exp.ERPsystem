import 'package:flutter_test/flutter_test.dart';
import 'package:offline_erp/main.dart';

void main() {
  test('currency formatting uses Indian grouping', () {
    expect(money(1234.5), contains('1,234.50'));
  });

  test('zero currency is stable', () {
    expect(money(0), contains('0.00'));
  });
}
