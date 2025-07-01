import 'package:flutter/material.dart';

/// Status card widget for displaying statistics
/// 
/// This widget displays a card with title, counts, and an icon
/// for showing status information in a consistent way.
/// 
/// @since 2025
class StatusCard extends StatelessWidget {
  final String title;
  final int count;
  final int activeCount;
  final int pendingCount;
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;

  const StatusCard({
    super.key,
    required this.title,
    required this.count,
    required this.activeCount,
    required this.pendingCount,
    required this.icon,
    required this.color,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 2,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    icon,
                    color: color,
                    size: 24,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _buildCountItem(
                      'Total',
                      count.toString(),
                      Colors.grey,
                    ),
                  ),
                  Expanded(
                    child: _buildCountItem(
                      'Active',
                      activeCount.toString(),
                      Colors.green,
                    ),
                  ),
                  Expanded(
                    child: _buildCountItem(
                      'Pending',
                      pendingCount.toString(),
                      Colors.orange,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCountItem(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            color: Colors.grey,
          ),
        ),
      ],
    );
  }
} 