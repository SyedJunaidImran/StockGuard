export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  price: number;
  imageUrl: string | null;
  active: boolean;
  category: Category | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ProductFilterParams {
  search?: string;
  categoryId?: number;
  active?: boolean;
  page?: number;
  size?: number;
  sortBy?: 'name' | 'price' | 'createdAt' | 'sku';
  sortDir?: 'ASC' | 'DESC';
}

export interface ProductCreatePayload {
  name: string;
  description?: string;
  sku: string;
  price: number;
  categoryId: number;
  imageUrl?: string;
  active?: boolean;
}

export interface ProductUpdatePayload {
  name: string;
  description?: string;
  sku: string;
  price: number;
  categoryId: number;
  imageUrl?: string;
  active?: boolean;
}
