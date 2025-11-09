import React, {useCallback, useMemo} from 'react';
import {
  ActivityIndicator,
  Button,
  FlatList,
  ListRenderItem,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useEnrollments} from '../../hooks/useEnrollments';
import {RootStackParamList} from '../../navigation/types';
import {StoredEnrollment} from '../../services/storage/enrollmentStorage';
import {useEnrollmentStore} from '../../state/enrollmentStore';

const sortEnrollments = (items: StoredEnrollment[]) =>
  [...items].sort((left, right) => {
    if (left.favorited && !right.favorited) {
      return -1;
    }
    if (!left.favorited && right.favorited) {
      return 1;
    }
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
  });

type HomeNavigation = NativeStackNavigationProp<RootStackParamList, 'Home'>;

export const HomeScreen: React.FC = () => {
  const navigation = useNavigation<HomeNavigation>();
  const {data, isLoading} = useEnrollments();
  const setSelected = useEnrollmentStore(store => store.setSelected);

  const enrollments = useMemo(() => (data ? sortEnrollments(data) : []), [data]);

  const navigateToWizard = () => navigation.navigate('EnrollmentWizard');

  const handleSelect = useCallback(
    (enrollment: StoredEnrollment) => {
      setSelected(enrollment.id);
      navigation.navigate('EnrollmentDetail', {enrollmentId: enrollment.id});
    },
    [navigation, setSelected],
  );

  const renderEnrollment = useCallback<ListRenderItem<StoredEnrollment>>(
    ({item}) => <EnrollmentListItem enrollment={item} onPress={handleSelect} />,
    [handleSelect],
  );

  const keyExtractor = useCallback((item: StoredEnrollment) => item.id, []);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Enrollments</Text>
        <Button title="Add" onPress={navigateToWizard} />
      </View>
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator />
        </View>
      ) : (
        <FlatList
          data={enrollments}
          keyExtractor={keyExtractor}
          contentContainerStyle={styles.listContent}
          renderItem={renderEnrollment}
          ListEmptyComponent={EmptyState}
        />
      )}
    </View>
  );
};

const EmptyState: React.FC = () => (
  <View style={styles.emptyState}>
    <Text style={styles.emptyText}>No enrollments yet. Tap Add to begin.</Text>
  </View>
);

type EnrollmentListItemProps = {
  enrollment: StoredEnrollment;
  onPress: (enrollment: StoredEnrollment) => void;
};

const EnrollmentListItem: React.FC<EnrollmentListItemProps> = ({enrollment, onPress}) => (
  <TouchableOpacity style={styles.card} onPress={() => onPress(enrollment)}>
    <View style={styles.cardHeader}>
      <Text style={styles.cardTitle}>{enrollment.integrationName}</Text>
      <Text style={styles.status}>{enrollment.status.toUpperCase()}</Text>
    </View>
    {enrollment.enrollmentName ? (
      <Text style={styles.cardSubtitle}>{enrollment.enrollmentName}</Text>
    ) : null}
    <Text style={styles.cardSubtitle}>{enrollment.tenantName}</Text>
    <Text style={styles.cardMeta}>
      Created {new Date(enrollment.createdAt).toLocaleDateString()}
    </Text>
  </TouchableOpacity>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 16,
    backgroundColor: '#0b0d11',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listContent: {
    gap: 12,
    paddingBottom: 32,
  },
  card: {
    backgroundColor: '#151923',
    borderRadius: 12,
    padding: 16,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#f4f7ff',
  },
  status: {
    fontSize: 12,
    fontWeight: '700',
    color: '#61d095',
  },
  cardSubtitle: {
    fontSize: 14,
    color: '#c2c8d5',
    marginBottom: 4,
  },
  cardMeta: {
    fontSize: 12,
    color: '#9aa3b6',
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
  },
  emptyText: {
    color: '#9aa3b6',
    fontSize: 16,
  },
});
